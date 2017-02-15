package com.makingdevs

import io.vertx.groovy.ext.web.handler.StaticHandler
import io.vertx.groovy.ext.web.Router
import io.vertx.groovy.ext.web.handler.BodyHandler
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.groovy.core.Vertx
import io.vertx.core.http.HttpHeaders
import io.vertx.groovy.ext.web.handler.sockjs.SockJSHandler
import io.vertx.groovy.ext.web.handler.StaticHandler
import io.vertx.groovy.ext.web.handler.CookieHandler
import io.vertx.groovy.ext.web.handler.SessionHandler
import io.vertx.groovy.ext.web.handler.UserSessionHandler
import io.vertx.groovy.ext.web.handler.RedirectAuthHandler
import io.vertx.groovy.ext.web.handler.FormLoginHandler
import io.vertx.groovy.ext.web.sstore.LocalSessionStore
import io.vertx.ext.auth.shiro.ShiroAuthRealmType
import io.vertx.groovy.ext.auth.shiro.ShiroAuth
import io.vertx.groovy.ext.mongo.MongoClient
import io.vertx.groovy.ext.auth.mongo.MongoAuth

def config = Vertx.currentContext().config()

//Configuration of Mongo
def mongoClient = MongoClient.createShared(vertx, config.mongo)

//configuracion externalizada
options = [ "config":config ]

//Configuración para hacer el worker
def senderOptions=options
senderOptions.worker=true

//Config for permissons
def opts = [
  outboundPermitteds:[
    [ address:"com.makingdevs.email.success" ]
  ]
]

//routers
def server = vertx.createHttpServer()
def router = Router.router(vertx)
router.route().handler(BodyHandler.create())

router.route().handler(CookieHandler.create())
router.route().handler(BodyHandler.create())
router.route().handler(SessionHandler.create(LocalSessionStore.create(vertx)))

//def authProvider = ShiroAuth.create(vertx, ShiroAuthRealmType.PROPERTIES, [:])
def authProvider = MongoAuth.create(mongoClient, [:])

/*
authProvider.insertUser("username","password", [],[]){ res ->
  println "*"*100
  println res.dump()
}
*/

router.route().handler(UserSessionHandler.create(authProvider))

router.route("/app/*").handler(RedirectAuthHandler.create(authProvider, "/static/"))

router.route("/app/*").handler(StaticHandler.create().setCachingEnabled(false).setWebRoot("app"))

router.post("/loginhandler").handler { routingContext ->
  def params = routingContext.request().params()
  def authInfo = [
    username:params.username,
    password:params.password
   ]

  authProvider.authenticate(authInfo, { res ->
    if (!res.failed()) {
      routingContext.setUser(res.result())
    }

    routingContext.response()
      .setStatusCode(302)
      .putHeader("location", "/app/")
      .end()
  })
}

router.route("/logout").handler({ contextResponse ->
    contextResponse.clearUser()
    contextResponse.response().putHeader("location", "/app/").setStatusCode(302).end()
})

// Create the event bus bridge and add it to the router.
def ebHandler = SockJSHandler.create(vertx).bridge(opts)
router.route("/eventbus/*").handler(ebHandler)

//Route to Index
router.route("/static/*").handler(
  StaticHandler.create().setCachingEnabled(false)
)

//Add new Email
router.post("/newEmail").handler { routingContext ->
  def params = routingContext.request().params()
  def email = [
  subject:params.subjectEmail,
  content:params.contentEmail,
  dateCreated:new Date().time,
  lastUpdate:new Date().time,
  version:1
  ]
  vertx.eventBus().send("com.makingdevs.emailer.new", email, { reply ->
    if (reply.succeeded()) {
      routingContext.response()
      .setStatusCode(201)
      .putHeader("content-type", "application/json; charset=utf-8")
      .end(Json.encodePrettily([msg:"Email Agregado Correctamente."]))
    }
  })
}

//Show all emails
router.route("/show").handler({ routingContext ->
  vertx.eventBus().send("com.makingdevs.emailer.show.total", "Show me", { reply ->
    if (reply.succeeded()) {
      routingContext.response()
      .setStatusCode(200)
      .putHeader("content-type", "application/json; charset=utf-8")
      .end(Json.encodePrettily(reply.result().body()))
    }

  })
})

//Remove an email
router.post("/remove").handler { routingContext ->
  def emailRemove= routingContext.request().getParam("idEmail")
  def query = ["_id":emailRemove]
  vertx.eventBus().send("com.makingdevs.emailer.remove", query, { reply ->
    if (reply.succeeded()) {
      routingContext.response()
      .setStatusCode(200)
      .putHeader("content-type", "application/json; charset=utf-8")
      .end(Json.encodePrettily([msg:"Email Eliminado Correctamente ${reply.result().body()}"]))
    }
  })
}

//Count all of emails
router.route("/countTotal").handler({ routingContext ->
  vertx.eventBus().send("com.makingdevs.emailer.count", "Dame el conteo", { reply ->
    if (reply.succeeded()) {
      routingContext.response()
      .setStatusCode(200)
      .putHeader("content-type", "application/json; charset=utf-8")
      .end(Json.encodePrettily([count:reply.result().body()]))
    }
  })
})

//Show one Email
router.post("/showEmail").handler { routingContext ->
  def emailId= routingContext.request().getParam("idEmail")
  def query = ["_id":emailId]
  vertx.eventBus().send("com.makingdevs.emailer.show.one", query, { reply ->
    if (reply.succeeded()) {
      routingContext.response()
      .setStatusCode(200)
      .putHeader("content-type", "application/json; charset=utf-8")
      .end(Json.encodePrettily(reply.result().body()))
    }
  })
}

//Show set of emails
router.post("/showSet").handler { routingContext ->

  def setValue=0
  setValue= routingContext.request().getParam("setValue")
  def options=[
  limit:10,
  skip:setValue.toInteger()
  ]
  //comunicate with verticle
  vertx.eventBus().send("com.makingdevs.emailer.show.set", options, { reply ->
    if (reply.succeeded()) {
      routingContext.response()
      .setStatusCode(200)
      .putHeader("content-type", "application/json; charset=utf-8")
      .end(Json.encodePrettily(reply.result().body()))
    }
  })
}

//Actualizar un email
router.post("/update").handler { routingContext ->
  //Obtener datos del update
  def paramsUpdate=routingContext.request().params()
  //mensaje que actualizará los datos, seguir el orden
  def message=[
  id:paramsUpdate.email_id,//id del email a actualizar
  subject:paramsUpdate.subjectEmail,//subject
  content:paramsUpdate.contentEmail,//contenido
  version:paramsUpdate.versionEmail.toInteger()+1,//Version
  update:new Date().time//fecha nueva en que esta siendo actualizado
  ]

  vertx.eventBus().send("com.makingdevs.emailer.update", message, { reply ->
    if (reply.succeeded()) {
      routingContext.response()
      .setStatusCode(200)
      .putHeader("content-type", "application/json; charset=utf-8")
      .end(Json.encodePrettily([msg:"Solicitud Enviada Correctamente."]))
    }
  })
}

//Mandar el preview a un email
router.post("/send").handler { routingContext ->

  def idTemplate= routingContext.request().getParam("email_id")
  def emailToSend= routingContext.request().getParam("emailPreview")
  def message=[
  id:idTemplate,
  email:emailToSend
  ]
  vertx.eventBus().send("com.makingdevs.emailer.send", message)

  routingContext.response()
  .setStatusCode(200)
  .putHeader("content-type", "application/json; charset=utf-8")
  .end(Json.encodePrettily([msg:"Solicitud Enviada Correctamente."]))
}

//Route para el servicio Web
router.post("/serviceEmail").handler { routingContext ->

  def authHeader = routingContext.request().getHeader("Authorization")

  if (!authHeader){
    routingContext.response()
      .setStatusCode(400)
      .putHeader("content-type", "application/json; charset=utf-8")
      .end(Json.encodePrettily([
        message:"Please login first. You have to include your username and password at Authentification Header"
      ]))
  }
  else{

  vertx.eventBus().send("com.makingdevs.emailer.decode", authHeader){ auth ->

  def arguments = auth.result().body()

  def authInfo = [
    username:arguments.username,
    password:arguments.password
   ]

  //Creando Sesión
  authProvider.authenticate(authInfo, { res ->
    if (!res.failed()) {
      routingContext.setUser(res.result())
      def emailerParams=[
        id:arguments.id,
        to:arguments.to,
        subject:arguments.subject,
        params:arguments.params
      ]

      if(routingContext.getBody().length()){
        def jsonResponse=routingContext.getBodyAsJson()
        vertx.eventBus().send("com.makingdevs.emailer.check", jsonResponse){ reply ->
        def status = 0
        def response = [:]

        if(reply.result.body() == "ok" ){
          status = 200
          vertx.eventBus().send("com.makingdevs.emailer.service", jsonResponse)
          response.message = "Request sent successfully"
        }
        else{
          status = 400
          response.message = "I can't do my job. You have the follow errors"
          response.errors = reply.result().body()
        }

        routingContext.response()
        .setStatusCode(status)
        .putHeader("Content-Type", "application/json; charset=utf-8")
        .end(Json.encodePrettily(response))
        }
     }
    else {
    //response
      routingContext.response()
      .setStatusCode(400)
      .putHeader("content-type", "application/json; charset=utf-8")
      .end(Json.encodePrettily([
        message:"Please send arguments to Emailer Service"
        ]))
    }
    }
    else{
      routingContext.response()
      .setStatusCode(400)
      .putHeader("content-type", "application/json; charset=utf-8")
      .end(Json.encodePrettily([
        message:"Please login with correct username and valid password."
      ]))
     }
    })
   }
  }
}

server.requestHandler(router.&accept).listen(8000)

//deploy verticles
vertx.deployVerticle("com/makingdevs/EmailerVerticle.groovy", options)
vertx.deployVerticle("com/makingdevs/SenderVerticle.groovy", senderOptions)
vertx.deployVerticle("com/makingdevs/HelperVerticle.groovy")
