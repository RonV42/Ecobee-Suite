/**
 *  Ecobee Sensor
 *
 *  Copyright 2015 Juan Risso
 *	Copyright 2017-2018 Barry A. Burke
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 * 
 *  See Changelog for change history
 *
 * <snip>
 *	1.4.0  - Major Release: renamed devices also
 *	1.4.01 - Added VersionLabel display
 *	1.4.02 - Fixed getMyId so that add/delete works properly
 *	1.4.03 - Fixed a typo
 *	1.4.04 - Updated for delayed add/delete function
 *	1.4.05 - Fixed add/deleteSensorFromProgram
 *	1.4.06 - Removed extra 'inactiveLabel: false', changed main() definition
 *	1.5.00 - Release number synchronization
 *	1.5.01 - Converted all math to BigDecimal for better precision
 *	1.6.00 - Release number synchronization
 *	1.6.10 - Resync for Ecobee Suite Manager-based reservations
 *	1.6.11 - Fix for off-line sensors
 *	1.6.12 - Added a modicom of compatibility with the (new) Samsung (Connect) app
 *	1.6.13 - Fixed sensor off-line reporting
 *	1.6.14 - Clean up digits display
 *  1.6.15 - Shortcut the 'TestingForInstall' installed()
 *	1.6.16 - Log uninstalls also
 *	1.7.00 - Initial Release of Universal Ecobee Suite
 *	1.7.01 - nonCached currentValue() on HE
 *	1.7.02 - Fixing private method issue caused by grails
 *  1.7.03 - Register new health check; auto reload new versions, avoid Health Check for test device install
 *  1.7.04 - Added importUrl for HE IDE
 *	1.7.05 - Optimized isST
 *	1.7.06 - Fixed importUrl for HE
 *	1.7.07 - Added ability to add/delete sensor from ANY Named program/schedule/climate
 */
String getVersionNum() 		{ return "1.7.07" }
String getVersionLabel() 	{ return "Ecobee Suite Sensor, version ${getVersionNum()} on ${getPlatform()}" }
def programIdList() 		{ return ["home","away","sleep"] } // we only support these program IDs for addSensorToProgram() - better to use the Name
import groovy.json.*

metadata {
	definition (
        name:         "Ecobee Suite Sensor", 
        namespace:    "sandood", 
        author:       "Barry A. Burke (storageanarchy@gmail.com)",
        mnmn:         "SmartThings",          // for the new Samsung (Connect) app
        vid:          "generic-motion",        // for the new Samsung (Connect) app
        importUrl:    "https://raw.githubusercontent.com/SANdood/Ecobee-Suite/master/devicetypes/sandood/ecobee-suite-sensor.src/ecobee-suite-sensor.groovy"
    ) 
    {		
		capability "Temperature Measurement"
		capability "Motion Sensor"
		capability "Sensor"
		capability "Refresh"
        capability "Health Check"
		
		attribute "Awake", "string"
		attribute "Away", "string"
		attribute "Home", "string"
		attribute "Sleep", "string"
		attribute "Fan Only", "string"
		attribute "programsList", "string"
		attribute "SmartRoom", "string"
		attribute "Wakeup", "string"	   
		attribute "currentProgramName", "string"
		attribute "decimalPrecision", "number"
		attribute "doors", "string"
		attribute "humidity", "string"
		attribute "temperatureDisplay", "string"
		attribute "thermostatId", "string"
		attribute "vents", "string"
		attribute "windows", "string"
		
		if (isST) {
			command "addSensorToProgram",	['string']
			command "deleteSensorFromProgram", ['string']
		} else {
			command "addSensorToProgram", 		[[name:'Program Name*', type:'STRING', description:'Add sensor to this Program Name']]
			command "deleteSensorFromProgram", 	[[name:'Program Name*', type:'STRING', description:'Delete sensor from this Program Name']]
		}
		
	// These commands are all really internal-use only
		command "addSensorToAway", []
		command "addSensorToHome", []
		command "addSensorToSleep", []
		command "deleteSensorFromAway", []
		command "deleteSensorFromHome", []
		command "deleteSensorFromSleep", []
		command "disableSmartRoom", []
		command "doRefresh", []
		command "enableSmartRoom", []
		command "noOp", []
		command "removeSensorFromAway", []
		command "removeSensorFromHome", []
		command "removeSensorFromSleep", []

	}

	simulator {
		// TODO: define status and reply messages here
	}
    
/*	COLOR REFERENCE

		backgroundColor:"#d28de0"		// ecobee purple/magenta
        backgroundColor:"#66cc00"		// ecobee green
		backgroundColor:"#2db9e7"		// ecobee snowflake blue
		backgroundColor:"#ff9c14"		// ecobee flame orange
        backgroundColor:"#00A0D3"		// SmartThings new "good" blue (replaced green)
*/    

	tiles(scale: 2) {
		multiAttributeTile(name:"temperatureDisplay", type: "generic", width: 6, height: 4){
			tileAttribute ("device.temperatureDisplay", key: "PRIMARY_CONTROL") {
				attributeState("temperature", label:'\n${currentValue}',
					backgroundColors: getTempColors(), defaultState: true)
			}
			tileAttribute ("device.motion", key: "SECONDARY_CONTROL") {
                attributeState "active", action:"noOp", nextState: "active", label:"Motion", icon:"https://raw.githubusercontent.com/StrykerSKS/SmartThings/master/smartapp-icons/ecobee/png/motion_sensor_motion.png"
				attributeState "inactive", action: "noOp", nextState: "inactive", label:"No Motion", icon:"https://raw.githubusercontent.com/StrykerSKS/SmartThings/master/smartapp-icons/ecobee/png/motion_sensor_nomotion.png"
            	attributeState "unknown", action: "noOp", label:"Off\nline", nextState: "unknown", icon: "https://raw.githubusercontent.com/StrykerSKS/SmartThings/master/smartapp-icons/ecobee/png/motion_sensor_noconnection.png"
             	attributeState "offline", action: "noOp", label:"Off\nline", nextState: "offline", icon: "https://raw.githubusercontent.com/StrykerSKS/SmartThings/master/smartapp-icons/ecobee/png/motion_sensor_noconnection.png"
 				attributeState "not supported", action: "noOp", nextState: "not supported", label: "N/A", icon:"https://raw.githubusercontent.com/StrykerSKS/SmartThings/master/smartapp-icons/ecobee/png/notsupported_x.png"
            }
		}

        valueTile("temperature", "device.temperature", width: 2, height: 2, canChangeIcon: true, decoration: 'flat') {
        	// Use the first version below to show Temperature in Device History - will also show Large Temperature when device is default for a room
            // 		The second version will show icon in device lists
			state("default", label:'${currentValue}°', /*unit:"dF",*/ backgroundColors: getTempColors(), defaultState: true)
            //state("default", label:'${currentValue}°', /* unit:"dF",*/ backgroundColors: getTempColors(), defaultState: true, icon:'st.Weather.weather2')
		}
        
        standardTile("motion", "device.motion", width: 2, height: 2, decoration: "flat") {
			state "active", action:"noOp", nextState: "active", label:"Motion", icon:"https://raw.githubusercontent.com/StrykerSKS/SmartThings/master/smartapp-icons/ecobee/png/motion_sensor_motion.png"
			state "inactive", action: "noOp", nextState: "inactive", label:"No Motion", icon:"https://raw.githubusercontent.com/StrykerSKS/SmartThings/master/smartapp-icons/ecobee/png/motion_sensor_nomotion.png"
            state "unknown", action: "noOp", label:"Offline", nextState: "unknown", icon: "https://raw.githubusercontent.com/StrykerSKS/SmartThings/master/smartapp-icons/ecobee/png/motion_sensor_noconnection.png"
            state "not supported", action: "noOp", nextState: "not supported", label: "N/A", icon:"https://raw.githubusercontent.com/StrykerSKS/SmartThings/master/smartapp-icons/ecobee/png/notsupported_x.png"
		}

        standardTile("refresh", "device.doRefresh", width: 1, height: 1, decoration: "flat") {
            state "refresh", action:"doRefresh", nextState: 'updating', label: "Refresh", defaultState: true, icon:"https://raw.githubusercontent.com/SANdood/Ecobee/master/icons/ecobee_refresh_green.png"
            state "updating", label:"Working", icon: "st.motion.motion.inactive"
		}

		standardTile("Home", "device.Home", width: 1, height: 1, decoration: "flat") {
			state 'on', action:"deleteSensorFromHome", nextState: 'updating', label:'on', icon:"https://raw.githubusercontent.com/SANdood/Ecobee/master/icons/schedule_home_blue_solid.png"
			state 'off', action: "addSensorToHome", nextState: 'updating', label:'off', icon:"https://raw.githubusercontent.com/SANdood/Ecobee/master/icons/schedule_home_blue.png"
            state 'updating', label:"Working...", icon: "st.motion.motion.inactive"
		}
        
        standardTile("Away", "device.Away", width: 1, height: 1, decoration: "flat") {
			state 'on', action:"deleteSensorFromAway", nextState: 'updating', label:'on', icon:"https://raw.githubusercontent.com/SANdood/Ecobee/master/icons/schedule_away_blue_solid.png"
			state 'off', action: "addSensorToAway", nextState: 'updating', label:'off', icon:"https://raw.githubusercontent.com/SANdood/Ecobee/master/icons/schedule_away_blue.png"
            state 'updating', label:"Working...", icon: "st.motion.motion.inactive"
		}

        standardTile("Sleep", "device.Sleep", width: 1, height: 1, decoration: "flat") {
            state 'on', action:"deleteSensorFromSleep", nextState: 'updating', label:'on', icon:"https://raw.githubusercontent.com/SANdood/Ecobee/master/icons/schedule_asleep_blue_solid.png"
			state 'off', action: "addSensorToSleep", nextState: 'updating', label:'off', icon:"https://raw.githubusercontent.com/SANdood/Ecobee/master/icons/schedule_asleep_blue.png"
            state 'updating', label:"Working...", icon: "st.motion.motion.inactive"
		}
        
        standardTile('vents', 'device.vents', width: 1, height: 1, decoration: 'flat') {
        	state 'default', label: '', action: 'noOp', nextState: 'default', icon: "https://raw.githubusercontent.com/SANdood/Ecobee/master/icons/blank.png", backgroundColor:"#ffffff"
            state 'notused', label: 'vents', action: 'noOp', nextState: 'notused', icon: "st.vents.vent", backgroundColor:"#ffffff"
            state 'open', label: 'open', action: 'noOp', nextState: 'open', icon: "st.vents.vent-open", backgroundColor:"#ff9c14"
            state 'closed', label: 'closed', action: 'noOp', nextState: 'closed', icon: "st.vents.vent", backgroundColor:"#d28de0"
        }

        standardTile('doors', 'device.doors', width: 1, height: 1, decoration: 'flat') {
        	state 'default', label: '', action: 'noOp', nextState: 'default', icon: "https://raw.githubusercontent.com/SANdood/Ecobee/master/icons/blank.png", backgroundColor:"#ffffff"
            state 'open', label: 'open', action: 'noOp', nextState: 'open', backgroundColor:"#00A0D3", icon: "st.contact.contact.open"
            state 'closed', label: 'closed', action: 'noOp', nextState: 'closed', backgroundColor:"#d28de0", icon: "st.contact.contact.closed"
        }
        
        standardTile('windows', 'device.windows', width: 1, height: 1, decoration: 'flat') {
        	state 'default', label: '', action: 'noOp', nextState: 'default', icon: "https://raw.githubusercontent.com/SANdood/Ecobee/master/icons/blank.png", backgroundColor:"#ffffff"
            state 'notused', label: 'windows', action: 'noOp', nextState: 'notused', icon: "st.Home.home9", backgroundColor:"#ffffff"
            state 'open', label: 'open', action: 'noOp', nextState: 'open', icon: "st.Home.home9", backgroundColor:"#d28de0"
            state 'closed', label: 'closed', action: 'noOp', nextState: 'closed', icon: "st.Home.home9", backgroundColor:"#00A0D3"
        }
        
         standardTile('SmartRoom', 'device.SmartRoom', width: 1, height: 1, decoration: 'flat') {
        	state 'default', label: '', action: 'noOp', nextState: 'default', icon: "https://raw.githubusercontent.com/SANdood/Ecobee/master/icons/blank.png", backgroundColor:"#ffffff"
            state 'active', label: 'active', action: 'disableSmartRoom', nextState: "disable", icon: "st.Home.home1", backgroundColor:"#00A0D3"
            state 'inactive', label: 'inactive', action: 'enableSmartRoom', nextState: "enable", icon: "st.Home.home2", backgroundColor:"#d28de0"
            state 'disabled', label: 'disabled', action: 'enableSmartRoom', nextState: "enable", icon: "st.Home.home2", backgroundColor:"#ff9c14"	// turned off in Smart Room settings
            state 'enable', label:"Working...", icon: "st.motion.motion.inactive", backgroundColor:"#ffffff"
            state 'disable', label:"Working...", icon: "st.motion.motion.inactive", backgroundColor:"#ffffff"
        }
        
        standardTile('blank', 'device.blank', width: 1, height: 1, decoration: 'flat') {
        	state 'default', label: '', action: 'noOp', icon: "https://raw.githubusercontent.com/SANdood/Ecobee/master/icons/blank.png"
        }
        
        standardTile("currentProgramIcon", "device.currentProgramName", height: 2, width: 2, decoration: "flat") {
			state "Home", 				action:"noOp", 	nextState:'Home', 				label: 'Home', 				icon: "https://raw.githubusercontent.com/SANdood/Ecobee/master/icons/schedule_home_blue.png"
			state "Away", 				action:"noOp", 	nextState:'Away', 				label: 'Away', 				icon: "https://raw.githubusercontent.com/SANdood/Ecobee/master/icons/schedule_away_blue.png"
            state "Sleep", 				action:"noOp", 	nextState:'Sleep', 				label: 'Sleep', 			icon: "https://raw.githubusercontent.com/SANdood/Ecobee/master/icons/schedule_asleep_blue.png"
            state "Awake", 				action:"noOp", 	nextState:'Awake', 				label: 'Awake', 			icon: "https://raw.githubusercontent.com/SANdood/Ecobee/master/icons/schedule_awake.png"
            state "Wakeup", 			action:"noOp", 	nextState:'Wakeup', 			label: 'Wakeup', 			icon: "https://raw.githubusercontent.com/SANdood/Ecobee/master/icons/schedule_awake.png"
			state "Auto", 				action:"noOp", 	nextState:'Auto', 				label: 'Auto', 				icon: "https://raw.githubusercontent.com/SANdood/Ecobee/master/icons/schedule_generic_chair_blue.png"
			state "Auto Away", 			action:"noOp", 	nextState:'Auto Away', 			label: 'Auto Away', 		icon: "https://raw.githubusercontent.com/SANdood/Ecobee/master/icons/schedule_away_blue.png" // Fix to auto version
            state "Auto Home", 			action:"noOp", 	nextState:'Auto Home', 			label: 'Auto Home', 		icon: "https://raw.githubusercontent.com/SANdood/Ecobee/master/icons/schedule_home_blue.png" // Fix to auto
            state "Hold", 				action:"noOp", 	nextState:"Hold", 				label: "Hold Activated", 	icon: "https://raw.githubusercontent.com/SANdood/Ecobee/master/icons/schedule_generic_chair_blue.png"
            state "Hold: Fan", 			action:"noOp", 	nextState:"Hold: Fan", 			label: "Hold: Fan", 		icon: "https://raw.githubusercontent.com/SANdood/Ecobee/master/icons/systemmode_fan_on_solid.png"
            state "Hold: Fan On", 		action:"noOp", 	nextState:'Hold: Fan on', 		label: "Hold: Fan On", 		icon: "https://raw.githubusercontent.com/SANdood/Ecobee/master/icons/systemmode_fan_on_solid_blue.png"
            state "Hold: Fan Auto",		action:"noOp", 	nextState:'Hold: Fan Auto',		label: "Hold: Fan Auto", 	icon: "https://raw.githubusercontent.com/SANdood/Ecobee/master/icons/systemmode_fan_on_blue.png"
            state "Hold: Circulate",	action:"noOp", 	nextState:'Hold: Circulate',	label: "Hold: Circulate",	icon: "https://raw.githubusercontent.com/SANdood/Ecobee/master/icons/systemmode_fan_on-1_blue..png"
			state "Hold: Home", 		action:"noOp", 	nextState:'Hold: Home', 		label: 'Hold: Home', 		icon: "https://raw.githubusercontent.com/SANdood/Ecobee/master/icons/schedule_home_blue_solid.png"
            state "Hold: Away", 		action:"noOp", 	nextState:'Hold: Away', 		label: 'Hold: Away', 		icon: "https://raw.githubusercontent.com/SANdood/Ecobee/master/icons/schedule_away_blue_solid.png"
            state "Hold: Sleep", 		action:"noOp", 	nextState:'Hold: Sleep', 		label: 'Hold: Sleep',		icon: "https://raw.githubusercontent.com/SANdood/Ecobee/master/icons/schedule_asleep_blue_solid.png"
      		state "Vacation", 			action:"noOp",	nextState:'Vacation', 			label: 'Vacation', 			icon: "https://raw.githubusercontent.com/SANdood/Ecobee/master/icons/schedule_vacation_blue_solid.png"
			state "Offline", 			action:"noOp",	nextState:'Offline', 			label: 'Offline', 			icon: "https://raw.githubusercontent.com/SANdood/Ecobee/master/icons/schedule_black_dot.png"
            state "Hold: Temp", 		action:'noOp',	nextState: 'Hold: Temp', 		label: 'Hold: Temp', 		icon: "https://raw.githubusercontent.com/SANdood/Ecobee/master/icons/thermometer_hold.png"
			state "default", 			action:"noOp", 	nextState:'default', 			label:'${currentValue}', 	icon: "https://raw.githubusercontent.com/SANdood/Ecobee/master/icons/schedule_generic_chair_blue.png"
		}

		main (['temperature']) //, "temperatureDisplay",])
		details(   ['temperatureDisplay',
        			'currentProgramIcon', 	'doors', 'windows', 'vents', 'SmartRoom',
                    						'Home',  'Away',  'Sleep', 'refresh'])
	}
    preferences {
       	input "dummy", "text", title: "${getVersionLabel()}", description: "."
	}
}

void refresh(force=false) {
    def tstatId = device.currentValue('thermostatId')
	LOG( "Refreshed - executing parent.pollChildren(${tstatId}) ${force?'(forced)':''}", 2, this, 'info')
	parent.pollChildren(tstatId,force)		// we have to poll our Thermostat to get updated
}

def doRefresh() {
    refresh(state.lastDoRefresh?((now()-state.lastDoRefresh)<6000):false)
    sendEvent(name: 'doRefresh', value: 'refresh', isStateChange: true, displayed: false)
    state.lastDoRefresh = now()	// reset the timer after the UI has been updated
}

void poll() {
	def tstatId = device.currentValue('thermostatId')
	LOG( "Polled - executing parent.pollChildren(${tstatId})", 2, this, 'info')
	parent.pollChildren(tstatId,false)		// we have to poll our Thermostat to get updated
}

// Health Check will ping us based on the frequency we configure in Ecobee (Connect) (derived from poll & watchdog frequency)
void ping() {
	def tstatId = device.currentValue('thermostatId')
	LOG( "Pinged - executing parent.pollChildren(${tstatId})", 2, null, 'info')
	parent.pollChildren(tstatId,true)		// we have to poll our Thermostat to get updated
}

void installed() {
	LOG("${device.label} being installed",2,null,'info')
    if (device.label?.contains('TestingForInstall')) return	// we're just going to be deleted in a second...
	updated()
}

void uninstalled() {
	LOG("${device.label} being uninstalled",2,null,'info')
}

void updated() {
	state?.hubPlatform = null
	getHubPlatform()
	LOG("${getVersionLabel()} updated",1,null,'info')
	state.version = getVersionLabel()
	
	if (!device.displayName.contains('TestingForInstall')) {
		// Try not to get hung up in the Health Check so that ES Manager can delete the temporary device
		sendEvent(name: 'checkInterval', value: 3900, displayed: false, isStateChange: true)  // 65 minutes (we get forcePolled every 60 minutes
		if (state.isST) {
			sendEvent(name: "DeviceWatch-Enroll", value: JsonOutput.toJson([protocol: "cloud", scheme:"untracked"]), displayed: false)
			updateDataValue("EnrolledUTDH", "true")
		}
	}
}

void noOp() {}

def generateEvent(Map results) {
	LOG("generateEvent(): parsing data ${results}",3,null,'trace')
	if (!state.version || (state.version != getVersionLabel())) updated()
	
	def startMS = now()
	
	Integer objectsUpdated = 0
	String tempScale = getTemperatureScale()
    def precision = device.currentValue('decimalPrecision')
    if (!precision) precision = (tempScale == 'C') ? 1 : 0
    String currentProgramName = state.isST ? device.currentValue('currentProgramName') : device.currentValue('currentProgramName', true)
    def isConnected = (currentProgramName != 'Offline')

	if(results) {
		String tempDisplay = ''
		results.each { name, value ->
			objectsUpdated++
			def linkText = getLinkText(device)
			def isChange = false
			def isDisplayed = true
			def event = [:]  // [name: name, linkText: linkText, handlerName: name]
           
			String sendValue = value as String
			switch(name) {
				case 'temperature':
					if ((sendValue == null) || (sendValue == 'unknown') || !isConnected) {
						// We are OFFLINE
						LOG("Warning: Remote Sensor (${device.displayName}:${name}) is OFFLINE. Please check the batteries or move closer to the thermostat.", 
							2, null, 'warn')
						sendEvent( name: 'temperatureDisplay', linkText: linkText, value: '451°', handlerName: "temperatureDisplay", 
								  descriptionText: 'Sensor is offline', /* isStateChange: true, */ displayed: true)
						// don't actually chhange the temperature - leave the old value
						// event = [name: name, linkText: linkText, descriptionText: "Sensor is Offline", handlerName: name, value: sendValue, isStateChange: true, displayed: true]
					} else {
						// must be online  
						// isChange = isStateChange(device, name, sendValue)
						isChange = true // always send the temperature, else HealthCheck will think we are OFFLINE

						// Generate the display value that will preserve decimal positions ending in 0
						if (isChange) {
							def dValue = value.toBigDecimal()
							if (precision == 0) {
								tempDisplay = roundIt(dValue, 0).toString() + '°'
								sendValue = roundIt(dValue, 0).toInteger()								// Remove decimals in device lists also
							} else {
								tempDisplay = String.format( "%.${precision.toInteger()}f", roundIt(dValue, precision.toInteger())) + '°'
							}
							sendEvent(name: 'temperatureDisplay', linkText: linkText, value: "${tempDisplay}", handlerName: 'temperatureDisplay', 
									  descriptionText: "Display temperature is ${tempDisplay}", isStateChange: true, displayed: false)
							event = [name: name, linkText: linkText, descriptionText: "Temperature is ${tempDisplay}", unit: tempScale, handlerName: name, 
									 value: sendValue, isStateChange: true, displayed: true]
							objectsUpdated++
						}
					}
					break;
				case 'motion':     
					if ( (sendValue == 'unknown') || !isConnected) {
						// We are OFFLINE
						LOG( "Warning: Remote Sensor (${device.displayName}:${name}) is OFFLINE. Please check the batteries or move closer to the thermostat.", 2, null, 'warn')
						sendValue = 'unknown'
					}

					isChange = isStateChange(device, name, sendValue.toString())
					if (isChange) event = [name: name, linkText: linkText, descriptionText: "Motion is ${sendValue}", handlerName: name, value: sendValue, 
										   isStateChange: true, displayed: true]
				    break;
				case 'currentProgramName':
					isChange = isStateChange(device, name, sendValue)
					if (isChange) {
						isConnected = (sendValue != 'Offline')		// update if it changes
						objectsUpdated++
						event = [name: name, linkText: linkText, value: sendValue, descriptionText: 'Program is '+sendValue.replaceAll(':',''), isStateChange: true, displayed: true]
					}
					break;
            case 'checkInterval':
            	event = [name: name, value: sendValue, /*isStateChange: true,*/ displayed: false]
				break;
			case 'Home':
			case 'Away':
			case 'Sleep':
			case 'vents':
			case 'doors':
			case 'windows':
			case 'SmartRoom':
			case 'decimalPrecision':
			case 'programsList':
			case 'thermostatId':
				isChange = isStateChange(device, name, sendValue)
				if (isChange) event = [name: name, linkText: linkText, handlerName: name, value: sendValue, isStateChange: true, displayed: false]
				break;
			default:
				// Must be a non-standard program name
				isChange = isStateChange(device, name, sendValue)
				if (isChange) event = [name: name, linkText: linkText, handlerName: name, value: sendValue, isStateChange: true, displayed: false]
				// Save non-standard Programs in a state variable
				if (state?."${name}" != sendValue) state."${name}" = sendValue // avoid unnecessary writes to state
				break;
            }			
			if (event != [:]) sendEvent(event)
		}
		//if (tempDisplay) {
		//	sendEvent( name: "temperatureDisplay", linkText: linkText, value: "${tempDisplay}", handlerName: "temperatureDisplay", descriptionText: "Display temperature is ${tempDisplay}", isStateChange: true, displayed: false)
		//}
	}
	def elapsed = now() - startMS
    LOG("Updated ${objectsUpdated} object${objectsUpdated!=1?'s':''} (${elapsed}ms)",2,this,'info')
}

//generate custom mobile activity feeds event
def generateActivityFeedsEvent(notificationMessage) {
	sendEvent(name: "notificationMessage", value: "$device.displayName $notificationMessage", descriptionText: "$device.displayName $notificationMessage", displayed: true)
}

void addSensorToHome() { addSensorToProgram('home') }
void addSensorToAway() { addSensorToProgram('away') }
void addSensorToSleep() { addSensorToProgram('sleep') }

def addSensorToProgram(programId) {
	LOG("addSensorToProgram(${programId}) - entry",3,this,'trace')
	def result = false
	def programsList = state.isST ? device.currentValue('programsList') : device.currentValue('programsList', true)
	if (programsList?.contains(programId)) {
		// Handle add by Name
		result = parent.addSensorToProgram(this, device.currentValue('thermostatId'), getSensorId(), programId)
		if (result) {
    		sendEvent(name: "${programId}", value: 'on', descriptionText: "Sensor added to ${programId} program", isStateChange: true, displayed: true)
            //runIn(5, refresh, [overwrite: true])
			if (!programIdList().contains(programId.toLowerCase())) state?."$programId" = 'on'
        } else {
           	sendEvent(name: "${programId}", value: 'off', isStateChange: true, displayed: false)
			if (!programIdList().contains(programId.toLowerCase())) state?."$programId" = 'off'
        }
	} else if (programIdList().contains(programId.toLowerCase())) {
		// Add by ID
    	if (device.currentValue(programId.capitalize()) != 'on') {
    		result = parent.addSensorToProgram(this, device.currentValue('thermostatId'), getSensorId(), programId.toLowerCase())
			if (result) {
    			sendEvent(name: "${programId.capitalize()}", value: 'on', descriptionText: "Sensor added to ${programId.capitalize()} program", isStateChange: true, displayed: true)
                //runIn(5, refresh, [overwrite: true])
            } else {
            	sendEvent(name: "${programId.capitalize()}", value: 'off', isStateChange: true, displayed: false)
            }
       	} else {
       		result = true
    	}
    } else {
    	LOG("addSensorToProgram(${programId}) - Bad argument, must be member of Program Names (${programsList[1..-2]}) or IDs (${programIdList().toString()[1..-2]})",1,null,'error')
        result = false
    }
    
    LOG("addSensorToProgram(${programId}) - ${result?'Succeeded':'Failed'}",2,this,'info')
    return result
}

void deleteSensorFromHome() { deleteSensorFromProgram('home') }
void deleteSensorFromAway() { deleteSensorFromProgram('away') }
void deleteSensorFromSleep() { deleteSensorFromProgram('sleep') }
void removeSensorFromHome() { deleteSensorFromProgram('home') }
void removeSensorFromAway() { deleteSensorFromProgram('away') }
void removeSensorFromSleep() { deleteSensorFromProgram('sleep') }

def deleteSensorFromProgram(programId) {
	LOG("deleteSensorFromProgram(${programId}) - entry",3,this,'trace')
    def result = false
	def programsList = state.isST ? device.currentValue('programsList') : device.currentValue('programsList', true)
	if (programsList?.contains(programId)) {
		// Handle delete by Name
		result = parent.deleteSensorFromProgram(this, device.currentValue('thermostatId'), getSensorId(), programId)
		if (result) {
    		sendEvent(name: "${programId}", value: 'off', descriptionText: "Sensor removed from ${programId} program", isStateChange: true, displayed: true)
            //runIn(5, refresh, [overwrite: true])
			if (!programIdList().contains(programId.toLowerCase())) state?."$programId" = 'off'
        } else {
           	sendEvent(name: "${programId}", value: 'on', isStateChange: true, displayed: false)
			if (!programIdList().contains(programId.toLowerCase())) state?."$programId" = 'on'
        }
	} else if (programIdList().contains(programId.toLowerCase())) {
		// Delete by ID
    	if (device.currentValue(programId.capitalize()) != 'off') {
    		result = parent.deleteSensorFromProgram(this, device.currentValue('thermostatId'), getSensorId(), programId.toLowerCase())
			if (result) {	
    			sendEvent(name: "${programId.capitalize()}", value: 'off', desciptionText: "Sensor removed from ${programId.capitalize()} program", isStateChange: true, displayed: true)
            } else {
            	sendEvent(name: "${programId.capitalize()}", value: 'on', isStateChange: true, displayed: false)
            }
        	//runIn(5, refresh, [overwrite: true]) 
       	} else {
        	result = true	// not in this Program anyway
        }
    } else {
		LOG("deleteSensorFromProgram(${programId}) - Bad argument, must be member of Program Names (${programsList[1..-2]}) or IDs (${programIdList().toString()[1..-2]})",1,null,'error')
        result = false
    }
    
   	LOG("deleteSensorFromProgram(${programId}) - ${result?'Succeeded':'Failed'}",2,this,'info')
    return result
}

void enableSmartRoom() {
	sendEvent(name: "SmartRoom", value: "enable", isSateChange: true, displayed: false)		// the Smart Room SmartApp should be watching for this
}

void disableSmartRoom() {
	sendEvent(name: "SmartRoom", value: "disable", isSateChange: true, displayed: false)		// the Smart Room SmartApp should be watching for this
}

String getSensorId() {
	def myId = []
    myId = device.deviceNetworkId.split('-') as List
    return (myId[2])
}
def roundIt( value, decimals=0 ) {
	return (value == null) ? null : value.toBigDecimal().setScale(decimals, BigDecimal.ROUND_HALF_UP) 
}
def roundIt( BigDecimal value, decimals=0 ) {
	return (value == null) ? null : value.setScale(decimals, BigDecimal.ROUND_HALF_UP) 
}

def debugLevel(level=3) {
	Integer debugLvlNum = (getParentSetting('debugLevel') ?: level) as Integer
    return ( debugLvlNum >= (level as Integer))
}

void LOG(message, Integer level=3, child=null, logType="debug", event=false, displayEvent=false) {
	def prefix = ""
	Integer dbgLvl = (getParentSetting('debugLevel') ?: level) as Integer
	if ( dbgLvl == 5 ) { prefix = "LOG: " }
	if ( dbgLvl >= (level as Integer) ) { 
    	log."${logType}" "${prefix}${message}"
        if (event) { debugEvent(message, displayEvent) }        
	}    
}

void debugEvent(message, displayEvent = false) {
	def results = [
		name: "appdebug",
		descriptionText: message,
		displayed: displayEvent
	]
	if ( debugLevel(4) ) { log.debug "Generating AppDebug Event: ${results}" }
	sendEvent (results)
}
	
def getTempColors() {
	def colorMap

	colorMap = [
		// Celsius Color Range
/*		[value: 0, color: "#1e9cbb"],
		[value: 15, color: "#1e9cbb"],
		[value: 19, color: "#1e9cbb"],

		[value: 21, color: "#44b621"],
		[value: 22, color: "#44b621"],
		[value: 24, color: "#44b621"],

		[value: 21, color: "#d04e00"],
		[value: 35, color: "#d04e00"],
		[value: 37, color: "#d04e00"],
		// Fahrenheit Color Range
		[value: 40, color: "#1e9cbb"],
		[value: 59, color: "#1e9cbb"],
		[value: 67, color: "#1e9cbb"],

		[value: 69, color: "#44b621"],
		[value: 72, color: "#44b621"],
		[value: 74, color: "#44b621"],

		[value: 76, color: "#d04e00"],
		[value: 95, color: "#d04e00"],
		[value: 99, color: "#d04e00"],
 */
		[value: 0, color: "#153591"],
		[value: 7, color: "#1e9cbb"],
		[value: 15, color: "#90d2a7"],
		[value: 23, color: "#44b621"],
		[value: 28, color: "#f1d801"],
		[value: 35, color: "#d04e00"],
		[value: 37, color: "#bc2323"],
		// Fahrenheit
		[value: 40, color: "#153591"],
		[value: 44, color: "#1e9cbb"],
		[value: 59, color: "#90d2a7"],
		[value: 74, color: "#44b621"],
		[value: 84, color: "#f1d801"],
		[value: 95, color: "#d04e00"],
		[value: 96, color: "#bc2323"],
        [value: 451, color: "#ff4d4d"] // Nod to the book and temp that paper burns. Used to catch when the device is offline
	]
}

def getStockTempColors() {
	def colorMap
    
    colorMap = [
    	[value: 32, color: "#153591"],
        [value: 44, color: "#1e9cbb"],
        [value: 59, color: "#90d2a7"],
        [value: 74, color: "#44b621"],
        [value: 84, color: "#f1d801"],
        [value: 92, color: "#d04e00"],
        [value: 98, color: "#bc2323"]
    ]       
}

// **************************************************************************************************************************
// SmartThings/Hubitat Portability Library (SHPL)
// Copyright (c) 2019, Barry A. Burke (storageanarchy@gmail.com)
String getPlatform() { (physicalgraph?.device?.HubAction ? 'SmartThings' : 'Hubitat') }	// if (platform == 'SmartThings') ...
boolean getIsST()     { (physicalgraph?.device?.HubAction ? true : false) }					// if (isST) ...
boolean getIsHE()     { (hubitat?.device?.HubAction ? true : false) }						// if (isHE) ...

String getHubPlatform() {
	def pf = getPlatform()
	state?.hubPlatform = pf			// if (state.hubPlatform == 'Hubitat') ...
											// or if (state.hubPlatform == 'SmartThings')...
	state?.isST = pf.startsWith('S')	// if (state.isST) ...
	state?.isHE = pf.startsWith('H')	// if (state.isHE) ...
	return pf
}
boolean getIsSTHub() { return state.isST }					// if (isSTHub) ...
boolean getIsHEHub() { return state.isHE }					// if (isHEHub) ...

def getParentSetting(String settingName) {
	// def ST = (state?.isST != null) ? state?.isST : isST
	return isST ? parent?.settings?."${settingName}" : parent?."${settingName}"
}
