
define ['lib/backbone'], () ->
  
  ID_COUNTER = 0

  class PropertyContainer extends Backbone.Model
    
    defaults :
      saveState : "saved"

    initialize : (opts) =>
      @properties = {}

    setDataModel : (dataModel) =>
      @dataModel = dataModel
      @properties = {}
      for key, value of @getItem().getProperties()
        @addProperty(key, value, false, {saved:true},{silent:true})

      @setSaved()
      @updatePropertyList()

    getItem : () =>
       @dataModel.get("data")
      
    getSelf : () =>
      @dataModel.get("data").getSelf()

    setKey : (id, key) =>
      duplicate = @hasKey(key, id)
      property = @getProperty(id)

      oldKey = property.key
      property.key = key
      
      if not @isCantSave()
        @setNotSaved()

      property.saved = false

      if duplicate
        property.isDuplicate = true
        @setCantSave()
      else
        property.isDuplicate = false

        if not @hasDuplicates()
          @setNotSaved()

        @getItem().removeProperty(oldKey)
        @getItem().setProperty(key, property.value)
      @updatePropertyList()
 
    setValue : (id, value) =>
      if not @isCantSave()
        @setNotSaved()

    deleteProperty : (id) =>
      if not isCantSave()
        @setNotSaved()

    addProperty : (key="", value="", updatePropertyList=true, propertyMeta={}, opts={}) =>
      id = @generatePropertyId()

      isDuplicate = if propertyMeta.isDuplicate? then true else false
      saved = if propertyMeta.saved? then true else false

      @properties[id] = {key:key, value:value, id:id, isDuplicate:isDuplicate, saved:saved}
      if updatePropertyList
        @updatePropertyList(opts)

    getProperty : (id) =>
      @properties[id]

    hasKey : (search, ignoreId=null) =>
      for id, property of @properties
        if property.key == search and id != ignoreId
          return true

      return false

    updatePropertyList : (opts={silent:true}) =>
      @set { propertyList : @getPropertyList() }, opts
      @change()

    getPropertyList : () =>
      arrayed = []
      for key, property of @properties
        arrayed.push(property)

      return arrayed

    hasDuplicates : =>
      for key, property of @properties
        if property.isDuplicate
          return true

      return false


    save : () =>
      @setSaveState("saving")
      @getItem().save().then @setSaved, @saveFailed


    saveFailed : (ev) =>
      @setNotSaved()


    setSaved : () =>
      @setSaveState("saved")

    setCantSave : () =>
      @setSaveState("cantSave")

    setNotSaved : () =>
      @setSaveState("notSaved")

    isSaved : =>
      @getSaveState() == "saved"

    isCantSave : () =>
      @getSaveState() == "cantSave"

    isNotSaved : => 
      @getSaveState() == "notSaved" or isCantSave()

    getSaveState : =>
      @get "saveState"
    
    setSaveState : (state, opts={}) =>
      @set { saveState : state }, opts


    generatePropertyId : () =>
      ID_COUNTER++
