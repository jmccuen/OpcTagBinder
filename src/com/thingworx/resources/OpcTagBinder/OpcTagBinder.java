package com.thingworx.resources.OpcTagBinder;

import com.thingworx.common.RESTAPIConstants;
import com.thingworx.common.exceptions.InvalidRequestException;
import com.thingworx.datashape.DataShape;
import com.thingworx.entities.utils.EntityUtilities;
import com.thingworx.logging.LogUtilities;
import com.thingworx.metadata.PropertyDefinition;
import com.thingworx.metadata.annotations.ThingworxServiceDefinition;
import com.thingworx.metadata.annotations.ThingworxServiceParameter;
import com.thingworx.metadata.annotations.ThingworxServiceResult;
import com.thingworx.networks.Network;
import com.thingworx.relationships.RelationshipTypes;
import com.thingworx.relationships.RelationshipTypes.ThingworxRelationshipTypes;
import com.thingworx.resources.Resource;
import com.thingworx.resources.entities.EntityServices;
import com.thingworx.things.Thing;
import com.thingworx.types.BaseTypes;
import com.thingworx.types.InfoTable;
import com.thingworx.types.TagCollection;
import com.thingworx.types.collections.AspectCollection;
import com.thingworx.types.collections.ValueCollection;
import com.thingworx.types.collections.ValueCollectionList;
import com.thingworx.types.primitives.TagCollectionPrimitive;

import java.util.Iterator;

import org.json.JSONObject;
import org.slf4j.Logger;

@SuppressWarnings("serial")
public class OpcTagBinder extends Resource {

	private static Logger _logger = LogUtilities.getInstance().getApplicationLogger(OpcTagBinder.class);

	public OpcTagBinder() {
		// TODO Auto-generated constructor stub. I don't think we actually need to do this, but at this point I'm too afraid to ask.
	}


	@ThingworxServiceDefinition(name = "UpdateTagBindings", description = "Create things, if they don't exist, add properties, if they don't exist, and bind them to the tag address", category = "", isAllowOverride = false, aspects = {
			"isAsync:false" })
	@ThingworxServiceResult(name = "result", description = "", baseType = "NOTHING", aspects = {})
	public void UpdateTagBindings(
			@ThingworxServiceParameter(name = "opcThing", description = "This is the name of a Thing that implements the IndustrialThingShape -- not the IndustrialGateway -- that will act as an intermediary to then locally bind the tags to the listed ThingName", baseType = "THINGNAME", aspects = {
					"isRequired:true", "thingShape:IndustrialThingShape" }) String opcThingName,
			@ThingworxServiceParameter(name = "network", description = "Optional network to add the entities to", baseType = "STRING") String networkName,
			@ThingworxServiceParameter(name = "topLevelEntity", description = "Top level entity for the network", baseType = "STRING") String topLevel,
			@ThingworxServiceParameter(name = "data", description = "", baseType = "INFOTABLE", aspects = {
					"isEntityDataShape:true", "dataShape:PTC.Factory.OpcTagMappingDataShape" }) InfoTable data) throws Exception {
		
		/*
		 * Note: the opcThing *is not* the same as the Opc Gateway. Kepware / TWX Industrial Connectivity / OPC Aggregator should connect
		 * to the *gateway* Thing and then you should create the opcThing based on the IndustrialThingShape, referencing the Opc Gateway 
		 * as the IndustrialThing property. This is done so that the same tag can be mapped to multiple entities, via local binding
		 * 
		 * This extension will work with or without the Mfg Apps, but does follow their mode (OPCGateway --> OPCThing --> RemoteThing
		 */
		
		
		EntityServices services = new EntityServices();
		//make sure all of the things exist and implement the correct templates / shapes
		if (!EntityUtilities.exists(opcThingName, ThingworxRelationshipTypes.Thing)) {
			throw new InvalidRequestException("Invalid OPC Thing -" + opcThingName + " - does not exist",
					RESTAPIConstants.StatusCode.STATUS_NOT_ACCEPTABLE);
		}
		Thing opcThing = (Thing) EntityUtilities.findEntity(opcThingName, ThingworxRelationshipTypes.Thing);
		
		if (!opcThing.implementsShape("IndustrialThingShape")) {
			throw new InvalidRequestException("Invalid OPC Thing -" + opcThingName + " - does not implement ThingShape: IndustrialThingShape",
					RESTAPIConstants.StatusCode.STATUS_NOT_ACCEPTABLE);
		}
		
		if (!opcThing.implementsTemplate("RemoteThing")) {
			throw new InvalidRequestException("Invalid OPC Thing -" + opcThingName + " - does not implement ThingTemplate: RemoteThing",
					RESTAPIConstants.StatusCode.STATUS_NOT_ACCEPTABLE);
		}
		
		String industrialThing = opcThing.getProperty("IndustrialThing").getValue().getStringValue();
		
		if (!EntityUtilities.exists(industrialThing, ThingworxRelationshipTypes.Thing)) {
			throw new InvalidRequestException("Invalid OPC Thing - " + opcThingName + " - IndustrialThing: " + 
											  industrialThing + " does not exist. Make sure IndustrialThing property is set correctly.",
					RESTAPIConstants.StatusCode.STATUS_NOT_ACCEPTABLE);
		}
		
		
		Thing opcGateway = (Thing) EntityUtilities.findEntity(industrialThing, ThingworxRelationshipTypes.Thing);
		

		if (!opcGateway.implementsTemplate("IndustrialGateway")) {
			throw new InvalidRequestException("Invalid OPC Thing -" + opcThingName + " - IndustrialThing: " + industrialThing + 
											  " does not implement ThingTemplate: IndustrialGateway",
					RESTAPIConstants.StatusCode.STATUS_NOT_ACCEPTABLE);
		}
		
		DataShape dataShape = (DataShape) EntityUtilities.findEntity("PTC.Factory.OpcTagMappingDataShape", ThingworxRelationshipTypes.DataShape);
		if (!data.getDataShape().equals(dataShape.getDataShape())) {
			throw new InvalidRequestException("Invalid DataShape on data parameter",
					  RESTAPIConstants.StatusCode.STATUS_NOT_ACCEPTABLE);
		}
		
		Network network = new Network();
	
		if (!networkName.isEmpty() && !topLevel.isEmpty()) {
			if (!EntityUtilities.exists(networkName, ThingworxRelationshipTypes.Network)) {
				services.CreateNetwork(networkName, "", null, null);
			}
			network = (Network)EntityUtilities.findEntity(networkName,ThingworxRelationshipTypes.Network);
			
			if (!network.IsInNetwork(topLevel)) {
				network.AddConnection("", topLevel, "childOf");
			}
		}
		
		ValueCollectionList rows = data.getRows();
		
		Iterator<ValueCollection> iterator = rows.iterator();
		
		while (iterator.hasNext()) {
			
			ValueCollection row = iterator.next();
			
			//these are the values we need in order to process the row
			//TODO: should we just skip the row if we're missing information, instead of killing the whole service?
			if (row.getStringValue("tagAddress").isEmpty()) {
				throw new InvalidRequestException("Invalid tag address: undefined",
						  RESTAPIConstants.StatusCode.STATUS_NOT_ACCEPTABLE);	
			}
			
			if (row.getStringValue("thingName").isEmpty()) {
				throw new InvalidRequestException("Invalid thingName: undefined",
						  RESTAPIConstants.StatusCode.STATUS_NOT_ACCEPTABLE);	
			}
			
			if (row.getStringValue("thingTemplate").isEmpty()) {
				throw new InvalidRequestException("Invalid thingTemplate: undefined",
						  RESTAPIConstants.StatusCode.STATUS_NOT_ACCEPTABLE);	
			}
			
			if (!EntityUtilities.exists(row.getStringValue("thingTemplate"), ThingworxRelationshipTypes.ThingTemplate)) {
				throw new InvalidRequestException("Invalid thingTemplate: " + row.getStringValue("thingTemplate") + " - Does not exist",
						  RESTAPIConstants.StatusCode.STATUS_NOT_ACCEPTABLE);
			}

			//set up our defaults
			//TODO: is it worth having persistence as a field in our InfoTable? I think it should never be the case for tag values?
			String sourceName = row.getStringValue("tagAddress").replace(".", "_"); // -_-
			String sourcePropertyName = row.getStringValue("tagAddress");
			Integer timeout = 0;
			String pushType = "VALUE";
			Double pushThreshold = 0.0;
			JSONObject remoteAspects = new JSONObject();
			Integer cacheTime = 0;
			String foldType = "NONE";
			Integer scanRate = 1000;
			String tagType = "Static";	
			String propertyDescription = row.getStringValue("propertyDescription");
			String baseType = this.getTwType(row.getStringValue("industrialDataType"));
			Boolean readOnly = (Boolean)row.getValue("readOnly");
			Boolean persistent = false;
			Boolean logged = false;
			String dataChangeType = "VALUE";
			Double dataChangeThreshold = 0.0;
			Boolean remote = true;
			String remotePropertyName = "";
			String defaultValue = null;
			
			//set them to the values if they exist
			if (!row.getStringValue("dataChangeType").isEmpty()) {	tagType = row.getStringValue("dataChangeType"); }
			if (!row.getStringValue("dataChangeThreshold").isEmpty()) { dataChangeThreshold = (Double)row.getValue("dataChangeThreshold"); }
			if (!row.getStringValue("timeout").isEmpty()) {	timeout = (Integer)row.getValue("timeout"); }
			if (!row.getStringValue("pushType").isEmpty()) {	pushType = row.getStringValue("pushType"); }
			if (!row.getStringValue("pushThreshold").isEmpty()) { pushThreshold = (Double)row.getValue("pushThreshold"); }
			if (!row.getStringValue("scanRate").isEmpty()) { scanRate = (Integer)row.getValue("scanRate"); }
			if (!row.getStringValue("tagType").isEmpty()) {	tagType = row.getStringValue("tagType"); }

			
			if (!opcThing.hasProperty(sourceName)) {
				//TODO: handle invalid characters for property names
				if (Character.isDigit(sourceName.charAt(0))) {
					sourceName = "_" + sourceName;
				}			
								
				opcThing.AddPropertyDefinition(sourceName, propertyDescription, baseType, "", "", readOnly, persistent, logged, dataChangeType, 
												dataChangeThreshold, remote, remotePropertyName, timeout, pushType, pushThreshold, defaultValue,
												remoteAspects);		
				
				//this shouldn't be necessary, but it turns out that it is!
				opcThing.RestartThing();					
			}
			
			///bind our remote property. For some reasons remote aspects are  json object instead of an AspectCollection. it is a mystery.
			remoteAspects.put("industrialDataType", row.getStringValue("industrialDataType"));
			remoteAspects.put("tagAddress", sourcePropertyName);
			remoteAspects.put("scanRate",scanRate);
			remoteAspects.put("tagType",tagType);
			remoteAspects.put("startType", "useDefaultValue");
			remoteAspects.put("source", "");
			remoteAspects.put("dataShape", "");
			
			opcThing.SetRemotePropertyBinding(sourceName, sourcePropertyName, timeout, pushType, pushThreshold, remoteAspects, cacheTime, foldType);
			
			
			String thingName = row.getStringValue("thingName");
			 
			//create our thing if it doesn't exist.
			if (!EntityUtilities.exists(thingName, ThingworxRelationshipTypes.Thing)) {
				
				try {
					services.CreateThing(thingName, row.getStringValue("thingDescription"), null , row.getStringValue("thingTemplate"));
				} catch(Exception err) {
					throw new InvalidRequestException("Error - unable to create thing: " + thingName + err.getMessage(),
							  RESTAPIConstants.StatusCode.STATUS_INTERNAL_ERROR);
				}
			
			}
			
			Thing thing = (Thing)EntityUtilities.findEntity(thingName, ThingworxRelationshipTypes.Thing);
			
			if (!thing.isEnabled()) {
				thing.EnableThing();
				thing.RestartThing();
			}
			
			//weirdly, you can't cast a TagCollectionPrimitive to a TagCollection
			//this seems to be a limitation of the TagCollectionPrimitive, other primitives work fine with casting.
			if (!row.getStringValue("modelTags").isEmpty()) {

				TagCollection tags = (TagCollection)BaseTypes.ConvertToObject(
													BaseTypes.ConvertToPrimitive(row.getStringValue("modelTags"),BaseTypes.TAGS), BaseTypes.TAGS);
				thing.SetTags(tags);
			}
			
			
			String projectName = row.getStringValue("projectName");
			if (!projectName.isEmpty()) {
				thing.SetProjectName(projectName);
			}
			
			String propertyName = row.getStringValue("propertyName");
			logged = (Boolean)row.getValue("logged");
			
			//ideally, the property should already exist on the template; but we'll add it to the thing if it doesn't
			if (!thing.hasProperty(propertyName)) {
				
				//TODO: Should we try to add the property to the template instead?
				_logger.warn(propertyName + " does not exist; it has been added to the Thing." + 
											"In most cases, properties should be defined on the ThingTemplate.");
				thing.AddPropertyDefinition(propertyName, propertyDescription, baseType, "", "", readOnly, persistent, logged, dataChangeType, 
						dataChangeThreshold, false, null, timeout, pushType, pushThreshold, defaultValue,
						new JSONObject());	
				
				thing.RestartThing();
			}
			
			//Locally bind the tag from the opc Thing to the desired thing
			//this is to support the Mfg Apps model where there is an intermediate thing, so that the same tag can be bound to multiple things
			thing.SetLocalPropertyBinding(propertyName, opcThingName, sourceName);	
			
			String parent = row.getStringValue("parent");
			
			if (!networkName.isEmpty() && !parent.isEmpty()) {
				
				if (!EntityUtilities.exists(parent,ThingworxRelationshipTypes.Things)) {
					try {
						services.CreateThing(parent, "", null, row.getStringValue("parentTemplate"));
					} catch (Exception e) {
						_logger.error("Unable to create parent: " + parent + " likely an invalid / non-existing template. Message: " + e.getMessage());
					}
					
				}				
				
				if (!network.IsInNetwork(parent)) {
					network.AddConnection(topLevel,parent,"childOf");
				}
				if (!network.IsChild(parent, thingName)) {
					try {
						network.AddConnection(parent, thingName, "childOf");
					} catch (Exception e){
						_logger.error("Unable to add network connection parent: " + parent + "to child: " + thingName + 
								      "This should only happen if there was an error creating the parent. Message: " + e.getMessage());
					}
				}
				
			}
			
		}
				
	}
	
	public String getTwType(String opcType) {
		
		switch (opcType) {
		   case "Char":
	       case "Byte":
	       case "Short":
	       case "Word":
	       case "Long":
	           return "INTEGER";
	       case "DWord": // INTEGER is signed 32 bit
	       case "Float":
	       case "Double":
	           return "NUMBER";
	       case "String":
	           return "STRING";
	       case "Bool":
	       case "Boolean":
	           return "BOOLEAN";
	       case "Date":
	               return "DATETIME";
	       default: // TODO: Can we support these?
		       case "LLong":
		       case "QWord":
		       case "BCD":
		       case "LBCD":
		           return "NOTHING";
		}
		
	}
	

}
