package com.makingdevs

def eb = vertx.eventBus()

//Consumer para decodificar base64
eb.consumer("com.makingdevs.emailer.decode"){ encode ->
  def decode = encode.body().decodeBase64()
  def messageDecode = new String(decode)
  def dataLogin = messageDecode.split(":")
  def credentials=[
    username:dataLogin[0].toString(),
    password:dataLogin[1].toString()
  ]
  encode.reply(credentials)
}

//Verify Params at service call
//Recibe: [:] con parámetros para armar el email
//Salida: ok si están los suficientes, [:] errores de campos que hacen falta
eb.consumer("com.makingdevs.emailer.check", { message ->
  def serviceParams=message.body()
  def errores=[:]
  if(!serviceParams.id) errores.id="Empty"
  if(!serviceParams.to) errores.to="Empty"
  if(!serviceParams.params) errores.params="Empty"
  if(!serviceParams.subject) errores.subject="Empty"
  if(!errores){
    message.reply("ok")
  }
  else{
    message.reply(errores)
  }
})


//Consumer para encargarse de armar y mandar el correo
//Recibe: [:] con valores del email encontrado
//Salida: Llama a los otros dos verticles para realizar el servicio
eb.consumer("com.makingdevs.emailer.serviceEmail", { message ->
  def dataEmail=message.body()
  def contentData=[
  content:message.body().content,
  params:message.body().params
  ]
  eb.send("com.makingdevs.emailer.buildEmail", contentData){ response ->
      dataEmail.html=response.result.body()
      //Mandando
      eb.send("com.makingdevs.emailer.sender", dataEmail)
      message.reply("Solicitud Enviada")
  }
  })

//Consumer para construir el contenido del correo
//Recibe: [:] Contenido, y params para hacer el match
//Salida: [String]=Contenido
eb.consumer("com.makingdevs.emailer.buildEmail", { message ->
  def params=message.body().params
  def content=message.body().content
  def engine=new groovy.text.SimpleTemplateEngine()
  def contentEmail=engine.createTemplate(content).make(params)
  message.reply(contentEmail.toString())
})
