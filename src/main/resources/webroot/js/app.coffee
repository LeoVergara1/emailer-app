class @.App
  constructor: ->
    @manager= new UrlManager()
    Verticle.init()
    Paginator.paginate()
    console.log "Mexican Debugger"

class @.UrlManager

  constructor: ->
    @emailerManager = new EmailerManager()
    @start()

  start: ->
    @routes =
      '/': @emailerManager.index
      '/newEmailer': @emailerManager.new
      '/readEmailers': @emailerManager.readEmailers
      '/previewEmailer/:id': @emailerManager.previewEmailer
      '/editEmailer/:id': @emailerManager.editEmailer
      '/deleteEmailer/:id':@emailerManager.delete
    @urlMappings()

  urlMappings: ->
    router = Router(@routes)
    router.init()

  @setRoute: ->
    router = Router(@routes)
    router.setRoute '/readEmailers'

class @.Verticle
  baseUrl = "http://localhost:8000"
  @init: ->
    eb = new EventBus('http://localhost:8000/eventbus')
    eb.onopen = ->
      eb.registerHandler 'com.makingdevs.email.success', (error, message) ->
        Materialize.toast 'Email Enviado\n ' + message.body, 4000

class @.Paginator
  @paginate: ->
    console.log "Paginate"
    $.ajax
      url: 'http://localhost:8000/countTotal'
      type: 'GET'
      success: (response) ->
        console.log response 
      error: ->
	console.log "Error al consultar conteo"
