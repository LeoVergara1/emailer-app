package com.makingdevs

import io.vertx.groovy.ext.unit.TestSuite
import io.vertx.groovy.core.Vertx
import io.vertx.groovy.core.http.HttpClient

def options = [ reporters: [[ to:"console" ]]]

//Create TestSuite
def suite = TestSuite.create("the_test_suite")

//Implement TestCase for senderVerticle consumers
suite.before({ context ->
  def async = context.async()
  //Verticle de buildEmail y check
  vertx.deployVerticle("src/main/groovy/com/makingdevs/HelperVerticle.groovy"){ ar ->
    context.assertTrue(ar.succeeded())
    async.complete()
  }
}).test("buildEmail_TestCase", { context ->
  def async = context.async()
  def emailContent='''Hola ${name}, te quiero decir:${msg}, saludos ${ami}'''
  def testService=[
    content:emailContent,
    params:[
      name:"MakingDevs",
      msg:"buenos dias",
      ami:"karlosins"
    ]
  ]
  def resultService='''Hola MakingDevs, te quiero decir:buenos dias, saludos karlosins'''
  vertx.eventBus().send("com.makingdevs.emailer.buildEmail", testService) { response ->
    context.assertEquals(resultService, response.result.body())
    async.complete()
  }
}).test("verificationCheck_Error",{ context ->
  def async=context.async()
  println "Verification test"
  def testCheck=[
    id:"1111",
    params:[
      uno:"1"
    ]
  ]
  vertx.eventBus().send("com.makingdevs.emailer.check", testCheck){ response ->
    def errorTest=[
      to:"Empty",
      subject:"Empty"
    ]
    context.assertEquals(errorTest, response.result.body())
    async.complete()
  }

}).test("verificationCheck_Ok", { context ->
  def async = context.async()
  def testService=[
    id:"abc1234567890",
    subject:"pruebas",
    to:"develop@me.com",
    content:"hi everyone",
    params:[
      name:"MakingDevs",
    ]
  ]
  vertx.eventBus().send("com.makingdevs.emailer.check", testService) { response ->
    def responseTest="ok"
    context.assertEquals(responseTest, response.result.body())
    async.complete()
  }
}).test("verificationEncoderAuth",{ context ->
  def async = context.async()
  def credentialsExample=[
    username:"admin",
    password:"admin"
  ]
  def encodeMessage="YWRtaW46YWRtaW4="
  vertx.eventBus().send("com.makingdevs.emailer.decode", encodeMessage) { response ->
    def credentialsDecode = response.result().body()
    context.assertEquals(credentialsExample, credentialsDecode)
    async.complete()
  }
}).after({ context ->
  println "Terminando las pruebas"
})

def completion = suite.run(options)

