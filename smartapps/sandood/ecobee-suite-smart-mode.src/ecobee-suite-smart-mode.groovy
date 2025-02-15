/**
 *  Ecobee Suite Smart Mode
 *
 *  Copyright 2018 Justin Leonard, Barry A. Burke
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
 * <snip>
 *	1.7.00 - Initial Release of Universal Ecobee Suite
 *	1.7.01 - Fixed thermostats*.auto()
 *  1.7.02 - Fixed SMS text entry
 *	1.7.03 - Fixing private method issue caused by grails
 *	1.7.04 - Trying to fix reservations
 *  1.7.05 - Fixing inside override issues
 *  1.7.06 - Don't do inside override until temp reaches setpoint+differential
 *  1.7.07 - On HE, changed (paused) banner to match Hubitat Simple Lighting's (pause)
 *  1.7.08 - Added settings option to allow internal temp/setpoint to override 'off' (but only if we hold the only 'off' Reservation)
 *	1.7.09 - When mode changes away, release reservations and set stats Mode to doneMode (if any)
 *	1.7.10 - Optimized isHE/isST, added Global Pause
 *	1.7.11 - Fixed yet another typo
 *	1.7.12 - Extended external temperature range, add Program change support
 *	1.7.13 - Changed displayName to Smart Mode, Programs & Setpoints
 *	1.7.14 - Rerun temperature checks when location.mode becomes valid again
 *	1.7.15 - Display current Mode & Program in appLabel
 *	1.7.16 - Clean up app label in sendMessage()
 *	1.7.17 - Fixed appLabel on ST
 *	1.7.18 - Added option to disable local display of log.debug() logs, support Notification devices on ST
 *	1.7.19 - Fixed appLabel yet again
 */
String getVersionNum() { return "1.7.19" }
String getVersionLabel() { return "Ecobee Suite Smart Mode, Programs & Setpoints Helper, version ${getVersionNum()} on ${getHubPlatform()}" }
import groovy.json.*

definition(
	name: 			"ecobee Suite Smart Mode",
	namespace: 		"sandood",
	author: 		"Justin J. Leonard & Barry A. Burke",
	description: 	"INSTALL USING ECOBEE SUITE MANAGER ONLY!\n\nSet Ecobee Mode, Program and/or Program Setpoints based on temperature & dewpoint.",
	category: 		"Convenience",
	parent: 		"sandood:Ecobee Suite Manager",
	iconUrl: 		"https://s3.amazonaws.com/smartapp-icons/Partner/ecobee.png",
	iconX2Url: 		"https://s3.amazonaws.com/smartapp-icons/Partner/ecobee@2x.png",
	singleInstance: false,
    pausable: 		true
)

preferences {
	page(name: "mainPage")
}

def mainPage() {
	boolean ST = isST
	boolean HE = !ST
	
	dynamicPage(name: "mainPage", title: (HE?'<b>':'') + "${getVersionLabel()}" + (HE?'</b>':''), uninstall: true, install: true) {
    	section(title: "") {
			String defaultLabel = "Smart Mode, Programs & Setpoints"
        	label(title: "Name for this ${defaultLabel} Helper", required: true, defaultValue: defaultLabel)
            if (!app.label) {
				app.updateLabel(defaultLabel)
				atomicState.appDisplayName = defaultLabel
			}
			if (HE) {
				if (app.label.contains('<span ')) {
					if (atomicState?.appDisplayName != null) {
						app.updateLabel(atomicState.appDisplayName)
					} else {
						String myLabel = app.label.substring(0, app.label.indexOf('<span '))
						atomicState.appDisplayName = myLabel
						app.updateLabel(myLabel)
					}
				}
			} else {
            	if (app.label.contains(' (paused)')) {
                	String myLabel = app.label.substring(0, app.label.indexOf(' (paused)'))
                    atomicState.appDisplayName = myLabel
                    app.updateLabel(myLabel)
                } else {
                	atomicState.appDisplayName = app.label
                }
            }
        	if(settings.tempDisable) { 
				paragraph "WARNING: Temporarily Paused - re-enable below."
			} else {
				input ("thermostats", "${ST?'device.ecobeeSuiteThermostat':'device.EcobeeSuiteThermostat'}", title: "Ecobee Thermostat(s)", required: true, 
					   multiple: true, submitOnChange: true)
			}
		}
        if (!settings?.tempDisable && (settings?.thermostats?.size()>0)) {
			section(title: (HE?'<b>':'') + "Outdoor Weather Source" + (HE?'</b>':'')) {
				input(name: 'tempSource', title: 'Monitor this weather source', type: 'enum', required: true, multiple: false,  
					  options: (ST?[
						  'ecobee':"Ecobee Thermostat's Weather", 
						  'location':"SmartThings/TWC Weather for ${location.name}", 
						  'sensors':'SmartThings Sensors',
						  'station':'SmartThings-based Weather Station DTH',
						  'wunder':'Weather Underground Station (obsolete)'
                    	]:[
						  'ecobee':"Ecobee Thermostat's Weather",  
						  'sensors':'Hubitat Sensors',
						  'station':'Hubitat-based Weather Station Device',
						  ]), submitOnChange: true
                )
				if (settings.tempSource) {
					if (settings.tempSource == 'location') {
                    	paragraph "Using The Weather Company weather for the current location (${location.name})."
                        if (!settings.latLon) input(name: "zipCode", type: 'text', title: 'Zipcode (Default is location Zip code)', defaultValue: getZIPcode(), required: true, submitOnChange: true )
                        if (location.latitude && location.longitude) input(name: "latLon", type: 'bool', title: "Use ${ST?'SmartThings':'Hubitat'} hub's GPS coordinates instead (better precision)?", submitOnChange: true)
						input(name: 'locFreq', type: 'enum', title: 'Temperature check frequency (minutes)', required: true, multiple: false, 
                        	options:['1','5','10','15','30','60','180'])
					} else if (settings.tempSource == 'sensors') {
                    	paragraph "Using ${ST?'SmartThings':'Hubitat'} sensors. Note: Both Temperature & Humidity sensors are required for dew point-based actions."
						input(name: 'thermometer', type: 'capability.temperatureMeasurement', title: "Which Temperature Sensor?", required: true, multiple: false)
                        input(name: 'humidistat', type: 'capability.relativeHumidityMeasurement', title: "Which Relative Humidity Sensor?",  
                        	required: (settings.dewBelowOverride), multiple: false) 
					} else if (settings.tempSource == "ecobee") {
                    	paragraph "Using weather data from the (notoriously inaccurate) Ecobee thermostat${(settings.thermostats.size()==1)?' '+settings.thermostats.displayName:':'}"
						if (settings.thermostats.size() > 1) {
							input(name: 'tstatTemp', type: 'enum', title: "Which Ecobee Thermostat?", required: true, multiple: false, submitOnChange: true,
                            		options:thermostats.displayName)
						}
					} else if (settings.tempSource == 'station') {
						paragraph "Using a ${ST?'SmartThings':'Hubitat'}-based Weather Station - please select ${ST?'ONE ':''}from the list of the supported Weather Station devices below..."
						if (ST) {
							input(name: "smartWeather", type: "device.smartWeatherStationTile", title: 'Which SmartWeather Station Tile?', required: false, 
									multiple: false, hideWhenEmpty: true)
							input(name: "smartWeather2", type: "device.smartWeatherStationTile2.0", title: 'Which SmartWeather Station Tile 2.0?', required: false, 
									multiple: false, hideWhenEmpty: true)
						}
						input(name: "meteoWeather", type: "${ST?'device.meteobridgeWeatherStation':'device.MeteobridgeWeatherStation'}", title: 'Which Meteobridge Weather Station?', required: false, 
                        		multiple: false, hideWhenEmpty: true)      
                    } else if (settings.tempSource == "wunder") {
                    	paragraph "Using a specific Weather Underground Weather Station"
						input(name: 'stationID', type: 'string', title: 'Enter WeatherUnderground Station identifier', defaultValue: "${settings.nearestPWS?getPWSID():''}", required: true)
                        input(name: 'nearestPWS', type: 'bool', title: 'Use nearest available station', options: ['true', 'false'], defaultValue: true, submitOnChange: true)
        				href(title: "Or, Search WeatherUnderground.com for your desired PWS",
        					description: 'After page loads, select "Change Station" for a list of weather stations.  ' +
        					'You will need to copy the station code into the PWS field above, in the form of "pws:STATIONID"',
             				required: false, style:'embedded',             
             				url: (location.latitude && location.longitude)? "http://www.wunderground.com/cgi-bin/findweather/hdfForecast?query=${location.latitude},${location.longitude}" :
             		 		"http://www.wunderground.com/q/${location.zipCode}")
                        input(name: 'pwsFreq', type: 'enum', title: 'Temperature check frequency (minutes)', required: true, multiple: false, options:['1','5','10','15','30','60','180'])
					}
				}
			}
			section(title: (HE?'<b>':'') + "Outdoor Temperature 'Above' Settings" + (HE?'</b>':'')) {
				// need to set min & max - get from thermostat range
       			input(name: "aboveTemp", title: "When the outdoor temperature is at or above...", type: 'decimal', description: "Enter decimal temperature (${settings.belowTemp?'optional':'required'})", 
                		range: getThermostatRange(), required: !settings.belowTemp, submitOnChange: true)
                input(name: "dewAboveTemp", title: "Or, (optionally) when the outdoor dewpoint is at or above...", type: 'decimal', description: "Enter decimal dewpoint", 
                		required: false, submitOnChange: true)
				if (settings.aboveTemp || settings.dewAboveTemp) {
					input(name: 'aboveMode', title: 'Set thermostat Mode to', type: 'enum', required: (!settings.aboveSetpoints && !settings.aboveSchedule), multiple: false, 
						  options:getThermostatModes(), submitOnChange: true)
                    if (settings.aboveMode == 'off') {
                    	paragraph "Note that Ecobee thermostats will still run fan circulation (if enabled) while the HVAC is in Off Mode"
                    }
					input(name: 'aboveSchedule', title: 'Set thermostat Program to', type: 'enum', required: (!settings.aboveSetpoints && ! settings.aboveMode), multiple: false, 
						  submitOnChange: true, options:getEcobeePrograms())
                    input(name: 'aboveSetpoints', title: 'Change Program Setpoints', type: 'bool', required: (!settings.aboveMode && !settings.aboveSchedule), defaultValue: false, submitOnChange: true) 
                    if (settings.aboveSetpoints) {
                    	if (!settings.aboveProgram && (!settings.aboveHeatTemp || !settings.aboveCoolTemp)) paragraph "You must select the program to modify and at least one setpoint to change"
                    	input(name: 'aboveProgram', title: 'Change Setpoints for Program', type: 'enum', required: true, submitOnChange: true, multiple: false, 
                        		options:getEcobeePrograms())
                    	input(name: 'aboveHeatTemp', title: "Desired heating setpoint (${getHeatRange()})", type: 'decimal', description: 'Default = no change...', required: (!settings.aboveCoolTemp), 
                        		range: getHeatRange(), submitOnChange: true)
                        input(name: 'aboveCoolTemp', title: "Desired cooling setpoint (${getCoolRange()})", type: 'decimal', description: 'Default = no change...', required: (!settings.aboveHeatTemp), 
                        		range: getCoolRange(), submitOnChange: true)
                    }
				}
			}
            section(title: (HE?'<b>':'') + "Outdoor Temperature 'Below' Settings" + (HE?'</b>':'')) {
            	input(name: "belowTemp", title: 'When the outdoor temperature is at or below...', type: 'decimal', description: "Enter decimal temperature (${settings.aboveTemp?'optional':'required'})", 
                		range: getThermostatRange(), required: !settings.aboveTemp, submitOnChange: true)
				if (settings.belowTemp) {
					input(name: 'belowMode', title: 'Set thermostat Mode to', type: 'enum', required: (!settings.belowSetpoints && !settings.belowSchedule), multiple: false, 
                    		options:getThermostatModes(), submitOnChange: true)
                    if (settings.belowMode == 'off') {
                    	paragraph "Note that Ecobee thermostats will still run fan circulation (if enabled) while the HVAC is in Off Mode"
                    }
					input(name: 'belowSchedule', title: 'Set thermostat Program to', type: 'enum', required: (!settings.belowSetpoints && !settings.belowMode), multiple: false,
						  submitOnChange: true, options:getEcobeePrograms())
                    input(name: 'belowSetpoints', title: 'Change Program Setpoints', type: 'bool', required: (!settings.belowMode && !settings.belowSchedule), defaultValue: false, submitOnChange: true) 
                    if (settings.belowSetpoints) {
                    	if (!settings.belowProgram && (!settings.belowHeatTemp || !settings.belowCoolTemp)) paragraph "You must select the program to modify and at least one setpoint to change"
                    	input(name: 'belowProgram', title: 'Change Setpoints for Program', type: 'enum', required: true, submitOnChange: true, multiple: false, 
                        		options:getEcobeePrograms())
                    	input(name: 'belowHeatTemp', title: "Desired heating setpoint (${getHeatRange()})", type: 'decimal', description: 'Default = no change...', required: (!settings.belowCoolTemp), 
                        		range: getHeatRange(), submitOnChange: true)
                        input(name: 'belowCoolTemp', title: "Desired cooling setpoint (${getCoolRange()})", type: 'decimal', description: 'Default = no change...', required: (!settings.belowHeatTemp), 
                        		range: getCoolRange(), submitOnChange: true)
                    }
				}
                if (settings.belowTemp && (settings.belowMode == 'off')) {
                	input(name: 'dewBelowOverride', type: 'bool', title: 'Dewpoint overrides below temp Off Mode?', required: true, defaultValue: false, submitOnChange: true)
                	if (settings.dewBelowOverride) {
                    	input(name: 'dewBelowTemp', type: 'decimal', title: 'Override Off Mode when dew point is at or above...', description: "Enter decimal dew point", required: true, 
                        		submitOnChange: true)       
                	}
            	}
            }
			if ((settings.belowTemp && settings.aboveTemp) && (settings.belowTemp != settings.aboveTemp)) {
            	section(title: (HE?'<b>':'') + "Outdoor Temperature 'Between' Settings" + (HE?'</b>':'')) {
					input(name: 'betweenMode', title: "When the outdoor temperature is between ${belowTemp}° and ${aboveTemp}°, set thermostat Mode to (optional)", type: 'enum', 
                    		required: false, multiple: false, options:getThermostatModes(), submitOnChange: true)
					input(name: 'betweenSchedule', title: 'Set thermostat Program to (optional)', type: 'enum', required: false, multiple: false, options:getEcobeePrograms())
            		input(name: 'betweenSetpoints', title: 'Change Program Setpoints', type: 'bool', required: false, defaultValue: false, submitOnChange: true) 
                    if (settings.betweenSetpoints) {
                    	if (!settings.betweenProgram && (!settings.betweenHeatTemp || !settings.betweenCoolTemp)) paragraph "You must select the program to modify and at least one setpoint to change"
                    	input(name: 'betweenProgram', title: 'Change Setpoints for Program', type: 'enum', required: true, submitOnChange: true, multiple: false, 
                        		options:getEcobeePrograms())
                    	input(name: 'betweenHeatTemp', title: 'Desired heating setpoint', type: 'decimal', description: 'Default = no change...', required: (!settings.betweenCoolTemp), 
                        		range: getHeatRange(), submitOnChange: true)
                        input(name: 'betweenCoolTemp', title: 'Desired cooling setpoint', type: 'decimal', description: 'Default = no change...', required: (!settings.betweenHeatTemp), 
                        		range: getCoolRange(), submitOnChange: true)
                    }
				}
            }
			if (settings.aboveSchedule || settings.belowSchedule || settings.betweenSchedule) {
				section(title: (HE?'<b>':'') + "Program Change Hold Type" + (HE?'</b>':'')) {
					input(name: "holdType", title: "Hold Type for Program changes (optional)", type: "enum", required: false, 
						  multiple: false, submitOnChange: true, defaultValue: "Ecobee Manager Setting",
						  options:["Until I Change", "Until Next Program", "2 Hours", "4 Hours", "Specified Hours", "Thermostat Setting", 
												"Ecobee Manager Setting"]) //, "Parent Ecobee (Connect) Setting"])
					if (settings.holdType=="Specified Hours") {
						input(name: 'holdHours', title:'How many hours (1-48)?', type: 'number', range:"1..48", required: true, description: '2', defaultValue: 2)
					} else if (settings.holdType=='Thermostat Setting') {
						paragraph("Thermostat Setting at the time of hold request will be applied.")
					} else if ((settings.holdType == null) || (settings.holdType == 'Ecobee Manager Setting') || (settings.holdType == 'Parent Ecobee (Connect) Setting')) {
						paragraph("Ecobee Manager Setting at the time of hold request will be applied")
					}
				}
			}
            section(title: (HE?'<b>':'') + 'Indoor Temperature Settings (Optional)' + (HE?'</b>':'')) {
            	if (getThermostatModes().contains('cool') && !settings.insideAuto) {
            		input(name: 'aboveCool', title: 'Set thermostat Mode to Cool if its indoor temperature is above its Cooling Setpoint (optional)?', type: 'bool', defaultValue: false, 
                          submitOnChange: true)
                    if (settings.aboveCool) {
                        paragraph "Mode will be set to 'cool' when the indoor temperature reaches the Cooling Setpoint PLUS the Cooling Setpoint Differential MINUS ${((getTemperatureScale()=='F')?'0.1':'0.055')}°" +
                              " - This is just before the thermostat will demand Cool from the HVAC"
                    }
                }
                if (getThermostatModes().contains('heat') && !settings.insideAuto) {
                	input(name: 'belowHeat', title: 'Set thermostat Mode to Heat if its indoor temperature is below its Heating Setpoint (optional)?', type: 'bool', defaultValue: false, 
                          submitOnChange: true)
                    if (settings.aboveCool) {
                        paragraph "Mode will be set to 'heat' when the indoor temperature reaches the Heating Setpoint MINUS the Heating Setpoint Differential PLUS ${((getTemperatureScale()=='F')?'0.1':'0.055')}°" +
                              " - This is just before the thermostat will demand Heat from the HVAC"
                    }
                }
                if (getThermostatModes().contains('auto') && !(settings.aboveCool || settings.belowHeat)) {
                	input(name: 'insideAuto', title: 'Set thermostat Mode to Auto if its indoor temperature is above or below its Setpoints (optional)?', type: 'bool', defaultValue: false, 
                          submitOnChange: true)
                    if (settings.insideAuto) {
                        paragraph "Mode will be set to 'auto' when the indoor temperature falls outside the Cooling and Heating Setpoints (adjusted by the appropriate differentials) - This is just before the" +
                                  " thermostat will demand Heat or Cool from the HVAC"
                    }
                }
                if ((settings.aboveCool || settings.belowHeat || settings.insideAuto) && (settings.aboveMode?.contains('off') || settings.belowMode?.contains('off') || settings.betweenMode?.contains('off'))) {
                    input(name: 'insideOverridesOff', title: "Allow the above indoor temperature/setpoint operations to change the Mode when the HVAC is 'off'?", type: 'bool', defaultValue: false, 
                          submitOnChange: true)
                }
			}
			section(title: (HE?'<b>':'') + "Additional Options" + (HE?'</b>':'')) {
            	input(name: "theModes",type: "mode", title: "Change Thermostat Mode only when the Location Mode is", multiple: true, required: false)
				if (settings.theModes) {
					input(name: 'doneMode', title: "When the Location Mode is no longer valid, reset thermostat mode to (optional)", type: 'enum', 
                    		required: false, multiple: false, options:getThermostatModes(), submitOnChange: true)
				}
				input(name: 'notify', type: 'bool', title: "Notify on Activations?", required: false, defaultValue: false, submitOnChange: true)
				paragraph HE ? "A 'HelloHome' notification is always sent to the Location Event log whenever an action is taken\n" : "A notification is always sent to the Hello Home log whenever an action is taken\n"
        	}            
			if (settings.notify) {
				if (ST) {
					section("Notifications") {
						input(name: 'pushNotify', type: 'bool', title: "Send Push notifications to everyone?", defaultValue: false, required: true, submitOnChange: true)
						input(name: "notifiers", type: "capability.notification", title: "", required: ((settings.phone == null) && !settings.speak && !settings.pushNotify),
							  multiple: true, description: "Select notification devices", submitOnChange: true)
						input(name: "phone", type: "text", title: "SMS these numbers (e.g., +15556667777; +441234567890)", required: false, submitOnChange: true)
						input(name: "speak", type: "bool", title: "Speak the messages?", required: true, defaultValue: false, submitOnChange: true)
						if (settings.speak) {
							input(name: "speechDevices", type: "capability.speechSynthesis", required: (settings.musicDevices == null), title: "On these speech devices", multiple: true, submitOnChange: true)
							input(name: "musicDevices", type: "capability.musicPlayer", required: (settings.speechDevices == null), title: "On these music devices", multiple: true, submitOnChange: true)
							if (settings.musicDevices != null) input(name: "volume", type: "number", range: "0..100", title: "At this volume (%)", defaultValue: 50, required: true)
						}
						if (!settings.phone && !settings.pushNotify && !settings.speak && !settings.notifiers) paragraph "WARNING: Notifications configured, but nowhere to send them!\n"
					}
				} else {		// HE
					section("<b>Use Notification Device(s)</b>") {
						input(name: "notifiers", type: "capability.notification", title: "", required: ((settings.phone == null) && !settings.speak), multiple: true, 
							  description: "Select notification devices", submitOnChange: true)
						paragraph ""
					}
					section("<b>Use SMS to Phone(s) (limit 10 messages per day)</b>") {
						input(name: "phone", type: "text", title: "SMS these numbers (e.g., +15556667777, +441234567890)", 
							  required: ((settings.notifiers == null) && !settings.speak), submitOnChange: true)
						paragraph ""
					}
					section("<b>Use Speech Device(s)</b>") {
						input(name: "speak", type: "bool", title: "Speak messages?", required: true, defaultValue: false, submitOnChange: true)
						if (settings.speak) {
							input(name: "speechDevices", type: "capability.speechSynthesis", required: (settings.musicDevices == null), title: "On these speech devices", multiple: true, submitOnChange: true)
							input(name: "musicDevices", type: "capability.musicPlayer", required: (settings.speechDevices == null), title: "On these music devices", multiple: true, submitOnChange: true)
							input(name: "volume", type: "number", range: "0..100", title: "At this volume (%)", defaultValue: 50, required: true)
						}
						paragraph ""
					}
				}
			}
        }
        section(title: (HE?'<b>':'') + "Temporary Disable" + (HE?'</b>':'')) {
        	input(name: "tempDisable", title: "Pause this Helper?", type: "bool", required: false, description: "", submitOnChange: true)                
		}
    	section (getVersionLabel()) {}
    }
}

void installed() {
	LOG("Installed with settings ${settings}", 4, null, 'trace')
    atomicState.aboveChanged = false
    atomicState.betweenChanged = false
    atomicState.belowChanged = false
    atomicState.dewpoint = null
    atomicState.humidity = null
	initialize()  
}
void uninstalled() {
	clearReservations()
}
void clearReservations() {
	thermostats?.each {
    	cancelReservation(getDeviceId(it.deviceNetworkId), 'modeOff' )
    }
}
void updated() {
	LOG("Updated with settings ${settings}", 4, null, 'trace')
	unsubscribe()
    unschedule()
    atomicState.aboveChanged = false
    atomicState.betweenChanged = false
    atomicState.belowChanged = false
    atomicState.dewpoint = null
    atomicState.humidity = null
    initialize()
}
boolean initialize() {
	LOG("${getVersionLabel()} Initializing...", 2, "", 'info')
	updateMyLabel()
	runEvery15Minutes(updateMyLabel)
	
	if (settings.tempDisable) {
    	clearReservations()
    	LOG("Temporarily Paused", 3, null, 'info')
    	return true
    }
    if (settings.debugOff) log.info "log.debug() logging disabled"
	
    if (settings.aboveCool || settings.belowHeat || settings.insideAuto) {
    	subscribe(thermostats, 'temperature', insideChangeHandler)
	} else {
		atomicState.insideOverride = [:]
	}
    
    if (settings.aboveTemp || settings.belowTemp) {
    	subscribe(thermostats, 'thermostatMode', thermostatModeHandler)
    }
    def tempNow
    def gu = getTemperatureScale()
	boolean ST = atomicState.isST
	switch( settings.tempSource) {
		case 'location':			
			// SmartThings Only
        	def WUname = (settings.latLon) ? 'getGPSTemp' : 'getZipTemp'
			if (settings.locFreq.toInteger() < 60) {
            	"runEvery${settings.locFreq}Minute${settings.locFreq!='1'?'s':''}"( "${WUname}" )
            } else {
            	def locHours = settings.locFreq.toInteger() / 60
                "runEvery${locHours}Hour${locHours!=1?'s':''}"( "${WUname}" )
            }
            def t = "${WUname}"()					// calls temperatureUpdate() & stores dewpoint
            if (t != null) tempNow = t as BigDecimal
			break;
		
		case 'sensors':
            if (settings.dewBelowOverride || (settings.dewAboveTemp != null)) {
            	if (settings.humidistat) { 
                	subscribe( settings.humidistat, 'relativeHumidity', humidityChangeHandler)
            	} else {
            		log.error "Dewpoint override(s) enabled, but no humidistat selected - initialization FAILED."
                	return false
                }
            }
            subscribe( settings.thermometer, 'temperature', tempChangeHandler)
            def latest = ST ? settings.thermometer.currentState("temperature") : settings.thermometer.currentState("temperature", true)
			def unit = latest.unit
            def t 
            if (latest.numberValue != null) {
            	t = roundIt(latest.numberValue, (unit=='C'?2:1))
            	if (dewBelowOverride) {
                	latest = ST ? settings.humidistat.currentState('humidity') : settings.humidistat.currentState('humidity', true)
            		if (latest.value != null) {
                    	def h = latest.numberValue
            			atomicState.humidity = h
                        LOG("Outside Humidity is: ${h}%",3,null,'info')
                		def d = calculateDewpoint( t, h, unit )
            			atomicState.dewpoint = d
                        LOG("Outside Dewpoint is: ${d}°${gu}",3,null,'info')
                   	}
                }
            	tempNow = t 
                temperatureUpdate(tempNow) 
            }
			break;
            
        case 'station':
        	if (settings.smartWeather) {
            	subscribe(settings.smartWeather, 'temperature', tempChangeHandler)
                def latest = ST ? settings.smartWeather.currentState('temperature') : settings.smartWeather.currentState('temperature', true)
                def t = latest.numberValue
                def unit = latest.unit
                if (t != null) {
                	t = latest.numberValue
                	if (dewBelowOverride) {
                		subscribe(settings.smartWeather, 'relativeHumidity', humidityChangeHandler)
                		latest = ST ? settings.smartWeather.currentState('relativeHumidity') : settings.smartWeather.currentState('relativeHumidity', true)
                		if (latest?.numberValue != null) {
                        	def h = roundIt(latest.numberValue, (unit=='C'?2:1))
                        	LOG("Outside Humidity is: ${h}%",3,null,'info')
                			def d = calculateDewpoint( t, h, unit )
                            atomicState.dewpoint = d
                            LOG("Outside Dewpoint is: ${d}°${gu}",3,null,'info')
                        }
                    }
                	tempNow = t 
                    temperatureUpdate(tempNow) 
                }
            } else if (settings.smartWeather2) {
            	def latest
                if (settings.dewBelowOverride) {
                	subscribe(settings.smartWeather2, 'dewpoint', dewpointChangeHandler)
                	latest = settings.smartWeather2.currentState('dewpoint')
                    if (latest?.numberValue != null) {
                    	def d = roundIt(latest.numberValue, (latest.unit=='C'?2:1))
                        atomicState.dewpoint = d
                        LOG("Outside Dewpoint is: ${d}°${gu}",3,null,'info')
                    }
                }
            	subscribe(settings.smartWeather2, 'temperature', tempChangeHandler)
                latest = settings.smartWeather2.currentState('temperature')
            	if (latest?.numberValue != null) { 
                	tempNow = roundIt(latest.numberValue, (latest.unit=='C'?2:1))
                    temperatureUpdate(tempNow) 
                }
            } else if (settings.meteoWeather) {
            	def latest
                if (settings.dewBelowOverride) {
                	subscribe(settings.meteoWeather, 'dewpoint', dewpointChangeHandler)
                	latest = ST ? settings.meteoWeather.currentState('dewpoint') : settings.meteoWeather.currentState('dewpoint', true)
                    if (latest?.numberValue != null) {
                    	def d = roundIt(latest.numberValue, (latest.unit=='C'?2:1))
                        atomicState.dewpoint = d
                        LOG("Outside Dewpoint is: ${d}°${gu}",3,null,'info')
                    }
                }
            	subscribe(settings.meteoWeather, 'temperature', tempChangeHandler)
                latest = ST ? settings.meteoWeather.currentState('temperature') : settings.meteoWeather.currentState('temperature', true)
            	if (latest?.numberValue != null) { 
                	tempNow = roundIt(latest.numberValue, (latest.unit=='C'?2:1))
                    temperatureUpdate(tempNow) 
                }
            } else if (settings.ambientWeather) {
            	def latest
                if (settings.dewBelowOverride) {
                	subscribe(settings.meteoWeather, 'dewPoint', dewpointChangeHandler)
                	latest = ST ? settings.meteoWeather.currentState('dewPoint') : settings.meteoWeather.currentState('dewPoint', true)
                    if (latest?.numberValue != null) {
                    	def d = roundIt(latest.numberValue, (latest.unit=='C'?2:1))
                        atomicState.dewpoint = d
                        LOG("Outside Dewpoint is: ${d}°${gu}",3,null,'info')
                    }
                }
            	subscribe(settings.meteoWeather, 'temperature', tempChangeHandler)
                latest = ST ? settings.meteoWeather.currentState('temperature') : settings.meteoWeather.currentState('temperature', true)
            	if (latest?.numberValue != null) { 
                	tempNow = roundIt(latest.numberValue, (latest.unit=='C'?2:1))
                    temperatureUpdate(tempNow) 
                }
            }
        	break;
		
		case "ecobee":
			def theStat = []
            def latest
			theStat = settings.thermostats.size() == 1 ? settings.thermostats[0] : settings.tstatTemp
            if (dewBelowOverride) {
            	subscribe(theStat, 'weatherDewpoint', dewpointChangeHandler)
            	latest = ST ? theStat.currentState('weatherDewpoint') : theStat.currentState('weatherDewpoint', true)
            	if (latest?.numberValue != null) {
                	def d = roundIt(latest.numberValue, (latest.unit=='C'?2:1))
                	atomicState.dewpoint = d
                    LOG("Outside Dewpoint is: ${d}°${gu}",3,null,'info')
                }
            }
            subscribe(theStat, 'weatherTemperature', tempChangeHandler)
            latest = ST ? theStat.currentState('weatherTemperature') : theStat.currentState('weatherTemperature', true)
            if (latest?.numberValue != null) {
            	tempNow = roundIt(latest.numberValue, (latest.unit=='C'?2:1))
                temperatureUpdate(tempnow) 
            }
			break;
		
		case 'wunder':
			if (settings.pwsFreq.toInteger() < 60) {
            	"runEvery${settings.pwsFreq}Minute${settings.pwsFreq!='1'?'s':''}"( 'getPwsTemp' )
            } else {
            	def pwsHours = settings.pwsFreq.toInteger() / 60
                "runEvery${pwsHours}Hour${pwsHours!=1?'s':''}"( 'getPwsTemp' )
            }
            def t = getPwsTemp()					// calls temperatureUpdate() and updates atomicState.dewpoint
            if (t != null) tempNow = t as BigDecimal
			break;
	}
    atomicState.locModeEnabled = settings.theModes ? settings.theModes : true
	if (settings.theModes) {
		subscribe(location, locationModeChangeHandler)
		atomicState.locModeEnabled = settings.theModes.contains(location.mode)
	} else { atomicState.locModeEnabled = true }
	
    if (tempNow) {
    	atomicState.temperature = tempNow
    	LOG("Initialization complete...current Outside Temperature is ${tempNow}°${gu} - checking...",2,null,'info')
		atomicTempUpdater()
        return true
    } else {
    	LOG("Initialization error...invalid temperature: ${tempNow}°${gu} - please check settings and retry", 2, null, 'error')
        return false
    }
}

def locationModeChangeHandler(evt) {
	updateMyLabel()
	if (!settings.theModes) return	// not using Location Mode filter
	if (settings.theModes.contains(evt.value)) {
		if (atomicState.temperature) atomicTempUpdater()
	}
}

def insideChangeHandler(evt) {
    def theTemp = evt.numberValue
    def unit = getTemperatureScale()
    def diffAdjust = ((unit == 'F') ? -0.1 : -0.055) as BigDecimal
    if (theTemp == null) {
    	LOG("Ignoring invalid temperature: ${theTemp}°${unit}", 2, null, 'warn')
        return
    }
    theTemp = roundIt(theTemp, (unit=='C'?2:1))
    LOG("${evt.device.displayName} Temperature is: ${theTemp}°${unit}",3,null,'info')
    
    def newMode = null
    def insideOverride = atomicState.insideOverride ?: [:]
    boolean coolOverride = false
    boolean heatOverride = false
	boolean ST = atomicState.isST
    String tid = getDeviceId(evt.device.deviceNetworkId)
    
    if (theTemp != null) {
    	def coolSP = (ST ? evt.device.currentValue('coolingSetpoint') : evt.device.currentValue('coolingSetpoint', true)) as BigDecimal
        if (coolSP != null) {
            coolSP += diffAdjust
            def coolDiff = (ST ? evt.device.currentValue('coolDifferential') : evt.device.currentValue('coolDifferential', true)) as BigDecimal
        	if (theTemp >= (coolSP + coolDiff)) {
				String cMode = ST ? evt.device.currentValue('thermostatMode') : evt.device.currentValue('thermostatMode', true)
            	if (settings.aboveCool) {
                	newMode = 'cool'
                    coolOverride = true
                } else if (settings.insideAuto && (cMode != 'cool')) {
                	newMode = 'auto'
                    // coolOverride = true
                }
            }
        }
        if (newMode == null) {
       		def heatSP = (ST ? evt.device.currentValue('heatingSetpoint') : evt.device.currentValue('heatingSetpoint', true)) as BigDecimal
            if (heatSP != null) {
            	def heatDiff = (ST ? evt.device.currentValue('heatDifferential') : evt.device.currentValue('heatDifferential', true)) as BigDecimal
				if (theTemp <= (heatSP - heatDiff)) {
                    heatSP += diffAdjust
					String cMode = ST ? evt.device.currentValue('thermostatMode') : evt.device.currentValue('thermostatMode', true)
                	if (settings.belowHeat) {
                    	newMode = 'heat'
                        heatOverride = true
                    } else if (settings.insideAuto && (cMode != 'heat')) {
                    	newMode = 'auto'
                        // heatOverride = true
                    }
                }
            }
        }
		
        def okMode = settings.theModes ? settings.theModes.contains(location.mode) : true
        if (okMode) {
        	atomicState.locModeEnabled = true
            if (newMode != null) {
                String cMode = isSt ? evt.device.currentValue('thermostatMode') : evt.device.currentValue('thermostatMode', true)
				// log.debug "newMode: ${newMode}, cMode: ${cMode}"
                if (cMode != newMode) {
                    boolean override = ((cMode != 'off') || (settings.insideOverridesOff && (!anyReservations(tid, 'modeOff') || ((countReservations(tid, 'modeOff') == 1) && haveReservation(tid, 'modeOff')))))
                    if (!override) {
                        // if Anybode else (but not me) has a reservation on this being off, I can't turn it back on
                        insideOverride[tid] = false
                        LOG("${evt.device.displayName} inside temp is ${theTemp}°${evt.unit}, but can't change to ${newMode} since ${getGuestList(tid,'offMode').toString()[1..-2]} have offMode reservations",2,null,'warn')
                        // Here's where we could subscribe to reservations and re-evaluate. For now, just wait for another inside Temp Change to occur
                    } else {
                        // not currently off or there are no modeOff reservations (other than my own), change away!
                        cancelReservation(tid, 'modeOff' )
                        insideOverride[tid] = (coolOverride || heatOverride)
                        evt.device.setThermostatMode(newMode)
                        LOG("${evt.device.displayName} temperature (inside) is ${theTemp}°${evt.unit}, changed thermostat to ${newMode} mode",3,null,'trace')
                        sendMessage("Thermostat ${evt.device.displayName} inside temperature is ${theTemp}°, so I changed it to ${newMode} mode")
                    }
                }
            } else {
                insideOverride[tid] = false
            }
        } else {
        	if (atomicState.locModeEnabled) {
                // Mode no longer valid, but it once was, so release any reservations and leave the HVAC where it is...
				LOG("Location Mode (${location.mode}) is no longer valid, releasing reservations${settings.doneMode?', and resetting Thermostat Mode to '+settings.doneMode.toString().capitalize():''}",2,null,'info')
                cancelReservation(tid, 'modeOff')
                if (!anyReservations(tid, 'modeOff')) {
					if (settings.doneMode) thermostats*."${settings.doneMode}"()
                }
                atomicState.locModeEnabled = false
            }
        }
    }
    atomicState.insideOverride = insideOverride
	updateMyLabel()
}

def thermostatModeHandler(evt) {
	// if the mode changes but we didn't do it, reset the atomicState modes as appropriate
    if ((settings.aboveTemp || settings.dewAboveTemp) && (evt.value == settings.aboveMode) && !atomicState.aboveChanged) {
    	atomicState.belowChanged = false
        atomicState.betweenChanged = false
    }
    else if (settings.belowTemp && (evt.value == settings.belowMode) && !atomicState.belowChanged) {
    	atomicState.aboveChanged = false
        atomicState.betweenChanged = false
    }
    else if (settings.aboveTemp && settings.belowTemp && settings.betweenMode && (evt.value == settings.betweenMode) && !atomicState.betweenChanged) {
    	atomicState.aboveChanged = false
        atomicState.belowChanged = false
    }
    if (evt.value != 'off') cancelReservation( getDeviceId(evt.device.deviceNetworkId), 'modeOff' ) // we're not off anymore, give up the reservation
	updateMyLabel()
}

def tempChangeHandler(evt) {
	boolean ST = atomicState.isST
	
    if (evt.numberValue != null) {
    	def t = roundIt(evt.numberValue, (evt.unit=='C'?2:1))
    	atomicState.temperature = t
        if (settings.dewBelowOverride || settings.dewAboveTemp) {
        	// We have to update the dewpoint every time the temperature (or humidity) changes
        	if (atomicState.humidity != null) {
            	// Somebody is updating atomicState.humidity, so we need to calculate the dewpoint
                // (Sources that provide dewpoint directly will not update atomicState.humidity)
            	if (settings.tempSource == 'sensors') {    
            		def latest = ST ? settings.humidistat.currentState('humidity') : settings.humidistat.currentState('humidity', true)
            		if (latest.numberValue != null) {
                    	def h = latest.numberValue
            			atomicState.humidity = h
                        LOG("Outside Humidity is: ${h}%",3,null,'info')
                		def d = calculateDewpoint( t, h, evt.unit )
            			atomicState.dewpoint = d
                        LOG("Outside Dewpoint is: ${d}°${evt.unit}",3,null,'info')
                        runIn(2, atomicTempUpdater, [overwrite: true] )		// humidity might be updated also
                        return
                   	}
                } else if ((settings.tempSource == 'station') && settings.smartWeather) {
                	def latest = ST ? settings.smartWeather.currentState('relativeHumidity') : settings.smartWeather.currentState('relativeHumidity', true)
                    if (latest.numberValue != null) {
                    	h = latest.numberValue
                        LOG("Outside Humidity is: ${h}%",3,null,'info')
                		def d = calculateDewpoint( t, h, unit )
                        atomicState.dewpoint = d
                        LOG("Outside Dewpoint is: ${d}°${evt.unit}",3,null,'info')
                        runIn(2, atomicTempUpdater, [overwrite: true] )		// humidity might be updated also
                        return
                    }
                }
            } else {
            	runIn(2, atomicTempUpdater, [overwrite: true] )				// wait for dewpoint to be updated also
                return
            }
        }
        // Aren't doing dewpoint stuff, so we can just update the temp directly
    	temperatureUpdate( t )
    }
}   

def dewpointChangeHandler(evt) {
	if (evt.numberValue != null) {
    	def d = roundIt(evt.numberValue, (evt.unit=='C'?2:1))
    	atomicState.dewpoint = d
        LOG("Outside Dewpoint is: ${d}°${evt.unit}",3,null,'info')
        runIn(2, atomicTempUpdater, [overwrite: true]) 		// wait for temp to be updated also
    }
}

def humidityChangeHandler(evt) {
	if (evt.numberValue != null) {
    	t = atomicState.temperature
        u = getTemperatureScale()
        atomicState.humidity = evt.numberValue
        LOG("Outside Humidity is: ${evt.numberValue}%",3,null,'info')
    	def d = calculateDewpoint(t, evt.numberValue, u)
        atomicState.dewpoint = d
        LOG("Outside Dewpoint is: ${d}°${getTemperatureScale()}",3,null,'info')
        runIn(2, atomicTempUpdater, [overwrite: true])
    }
}

def atomicTempUpdater() {
	temperatureUpdate( atomicState.temperature )
}

def temperatureUpdate( temp ) {
	if (temp != null) temperatureUpdate(temp as BigDecimal)
}
def temperatureUpdate( BigDecimal temp ) {
	def unit = getTemperatureScale()
    if (temp == null) {
    	LOG("Ignoring invalid temperature: ${temp}°${unit}", 2, null, 'warn')
        return false
    }
    
    temp = roundIt(temp, (unit=='C'?2:1))
    atomicState.temperature = temp
    LOG("Outside Temperature is: ${temp}°${unit}",3,null,'info')
    
    def okMode = theModes ? theModes.contains(location.mode) : true
    if (okMode) {
    	atomicState.locModeEnabled = true
    } else {
    	if (atomicState.locModeEnabled) {
			LOG("Location Mode (${location.mode}) is no longer valid, releasing reservations${settings.doneMode?', and resetting Thermostat Mode to '+settings.doneMode.toString().capitalize():''}",2,null,'info')
        	// release all the reservations 
        	settings.thermostats.each { 
            	def tid = getDeviceId(it.deviceNetworkId)
            	cancelReservation(tid, 'modeOff')
            	if (!anyReservations(tid, 'modeOff')) {
					if (settings.doneMode) thermostats*."${settings.doneMode}"() 
            	}
            }
            atomicState.locModeEnabled = false
        }
        return
    }
    
    def desiredMode = null
	def desiredProgram = null
	if ((settings.aboveTemp && (temp >= settings.aboveTemp)) || (settings.dewAboveTemp && (atomicState.dewpoint >= settings.dewAboveTemp))) {
    	if (!atomicState.aboveChanged) {
			desiredMode = 		settings.aboveMode
			desiredProgram = 	settings.aboveSchedule
            if (settings.aboveSetpoints) {
            	changeSetpoints(settings.aboveProgram, settings.aboveHeatTemp, settings.aboveCoolTemp)
            }
            atomicState.aboveChanged = true
            atomicState.betweenChanged = false
            atomicState.belowChanged = false
        }
	} else if (settings.belowTemp && (temp <= settings.belowTemp)) {
    	if (!atomicState.belowChanged) {
        	// We haven't already changed to belowMode/belowSchedule
			if (settings.belowMode && (settings.belowMode != 'off')) {
				// not turning off
				desiredMode = settings.belowMode
				desiredProgram = settings.belowSchedule
                if (settings.belowSetpoints) {
            		changeSetpoints(settings.belowProgram, settings.belowHeatTemp, settings.belowCoolTemp)
            	}
			} else if ((settings.belowMode && (settings.belowMode == 'off')) && (!settings.dewBelowOverride || (settings.dewBelowTemp > atomicState.dewpoint))) {
				// belowMode is 'off', but we don't need to do dewpointOverride just adjust the Program and/or Setpoints
				desiredMode = null
				desiredProgram = settings.belowSchedule
                if (settings.belowSetpoints) {
            		changeSetpoints(settings.belowProgram, settings.belowHeatTemp, settings.belowCoolTemp)
            	}
			} else if (!settings.belowMode) {
            	// belowMode changes aren't configured, just do schedule and setpoints
				desiredMode = null
				desiredProgram = settings.belowSchedule
				if (settings.belowSetpoints) {
            		changeSetpoints(settings.belowProgram, settings.belowHeatTemp, settings.belowCoolTemp)
            	}
			} else {
				// assert ((settings.belowMode == 'off') && (settings.dewBelowOverride && (settings.dewBelowTemp < atomicState.dewpoint))) // don't turn off
				desiredMode = null
				desiredProgram = settings.belowSchedule
				if (settings.belowSetpoints) {
            		changeSetpoints(settings.belowProgram, settings.belowHeatTemp, settings.belowCoolTemp)
            	}
				
			}
			atomicState.aboveChanged = false
            atomicState.betweenChanged = false
            atomicState.belowChanged = true
        } else {
        	// We have prior changed to the belowMode/belowSchedule - now we have to check if dewpoint is still below the limit
            if ((settings.belowMode == 'off') && settings.dewBelowOverride && (settings.dewBelowTemp <= atomicState.dewpoint)) {
            	// Uh-oh, the dewpoint has risen into the bad land, and we are supposedly off at the moment
            	if (settings.betweenMode || settings.betweenSetpoints || settings.betweenSchedule) {
                	// We have a between mode - let's change back to that
                	desiredMode = 		settings.betweenMode
					desiredProgram = 	settings.betweenSchedule
                    if (settings.betweenSetpoints) {
            			changeSetpoints(settings.betweenProgram, settings.betweenHeatTemp, setting.betweenCoolTemp)
            		}
            		atomicState.aboveChanged = false
            		atomicState.betweenChanged = true
            		atomicState.belowChanged = true		// so we don't change it again
                } else if (settings.aboveMode || settings.aboveSetpoints) {
                	// OK, no between mode. But we have an above mode - switch to that
                	desiredMode = 		settings.aboveMode
					desiredProgram = 	settings.aboveProgram
                    if (settings.aboveSetpoints) {
            			changeSetpoints(settings.aboveProgram, settings.aboveHeatTemp, setting.aboveCoolTemp)
            		}
                    atomicState.aboveChanged = true
                    atomicState.betweeChanged = false
                    atomicState.belowChanged = true		// so we don't change it again...
                }
            } else if (!settings.belowMode || (!settings.dewBelowTemp || (settings.dewBelowTemp > atomicState.dewpoint))) {
				// No belowMode, or not doing dewpointOverride - return to belowMode settings
				desiredMode = settings.belowMode
				desiredProgram = settings.belowSchedule
				if (settings.belowSetpoints) {
            		changeSetpoints(settings.belowProgram, settings.belowHeatTemp, settings.belowCoolTemp)
            	}
				atomicState.aboveChanged = false
            	atomicState.betweenChanged = false
            	atomicState.belowChanged = true
			}
        }
	} else if ((settings.aboveTemp || (settings.dewAboveTemp && (atomicState.dewpoint < settings.dewAboveTemp))) && settings.belowTemp && settings.betweenMode) {
    	if (!atomicState.betweenChanged) {
			desiredMode = 		settings.betweenMode
			desiredProgram = 	settings.betweenSchedule
            if (settings.betweenSetpoints) {
            	changeSetpoints(settings.betweenProgram, settings.betweenHeatTemp, setting.betweenCoolTemp)
            }
            atomicState.aboveChanged = false
            atomicState.betweenChanged = true
            atomicState.belowChanged = false
        }
	}
	
	if ((desiredMode != null) || (desiredProgram != null)) {
    	String changeNames = ""
        String sameNames = ""
        def insideOverride = atomicState.insideOverride
		settings.thermostats.each { 
        	String cMode = atomicState.isST ? it.currentValue('thermostatMode') : it.currentValue('thermostatMode', true)
			String cProgram = atomicState.isST ? it.currentValue('currentProgramName') : it.currentValue('currentProgramName', true)
            String tid = getDeviceId(it.deviceNetworkId)
            if (!insideOverride || !insideOverride.containsKey(tid) || !insideOverride[tid]) {
                if ((cMode && (cMode != desiredMode)) || (cProgram && (cProgram != desiredProgram))) {
                    if ((desiredMode == 'off') && (cMode != 'off')) {
                        makeReservation(tid, 'modeOff')
                        it.setThermostatMode( 'off' )
                    } else {
                        // Desired mode IS NOT 'off'
                        if (cMode == 'off') {
                            cancelReservation(tid,'modeOff')
                            if (countReservations(tid, 'modeOff') == 0) {
                                // nobody else has a reservation on modeOff
                                if (desiredMode && (cMode != desiredMode)) it.setThermostatMode(desiredMode)
								if (desiredProgram && (cProgram != desiredProgram)) {
									def sendHoldType = whatHoldType(stat)
									def sendHoldHours = null
									if ((sendHoldType != null) && sendHoldType.toString().isNumber()) {
										sendHoldHours = sendHoldType
										sendHoldType = 'holdHours'
									}
									LOG("sendHoldType: ${sendHoldType}, sendHoldHours: ${sendHoldHours}",3,null,'info')
									it.setThermostatProgram(desiredProgram, sendHoldType, sendHoldHours)
								}
                                changeNames += changeNames ? ", ${it.displayName}" : it.displayName
                            } else {
                                // somebody else still has a 'modeOff' reservation so we can't turn it on
                                def reservedBy = getGuestList(tid,'modeOff')
                                LOG("Reservations: ${reservedBy}", 3, null, 'debug')
								if (reservedBy == []) reservedBy = ['somebody']
                                def msg = "The Outside  Temperature is ${temp}°${unit}, but I can't change ${it.displayName} to ${desiredMode} Mode because ${reservedBy.toString()[1..-2]} hold 'modeOff' reservations"
                                LOG(msg ,2,null,'warn')
                                sendMessage(msg)
                                // here's where we COULD subscribe to the reservations to see when we can turn it back on. For now, let's just let whomever is last deal with it
                            }
                        } else {
                            // Not off currently, so we can change freely
                            cancelReservation(tid, 'modeOff')	// just in case
                            if (desiredMode && (cMode != desiredMode)) it.setThermostatMode(desiredMode)
							if (desiredProgram && (cProgram != desiredProgram)) {
								def sendHoldType = whatHoldType(stat)
								def sendHoldHours = null
								if ((sendHoldType != null) && sendHoldType.toString().isNumber()) {
									sendHoldHours = sendHoldType
									sendHoldType = 'holdHours'
								}
								LOG("sendHoldType: ${sendHoldType}, sendHoldHours: ${sendHoldHours}",3,null,'info')
								it.setThermostatProgram(desiredProgram, sendHoldType, sendHoldHours)
							}
                        }
                    }
                } else {
                    // already running the mode we want
                    (desiredMode == 'off') ? makeReservation(tid, 'modeOff') : cancelReservation(tid, 'modeOff')
                    sameNames += sameNames ? ", ${it.displayName}" : it.displayName
                }
            } else {
                LOG("Inside Temperature has overridden calculated Thermostat Mode, will not change ${it.displayName} to ${desiredMode} mode", 2, null, 'info')
            }
		}
        def multi=0
        if (changeNames) {
        	LOG("Temp is ${temp}°${unit}, changed ${changeNames} to ${desiredMode} mode",3,null,'trace')
        	sendMessage("The temperature is ${temp}°${unit}, so I changed thermostat${changeNames.size() > 1?'s':''} ${changeNames} to ${desiredMode} mode")
        }
        if (sameNames) LOG("Temp is ${temp}°${unit}, ${sameNames} already in ${desiredMode} mode",3,null,'info')
	}
	updateMyLabel()
}

void changeSetpoints( program, heatTemp, coolTemp ) {
	def unit = getTemperatureScale()
	settings.thermostats.each { stat ->
    	LOG("Setting ${stat.displayName} '${program}' heatingSetpoint to ${heatTemp}°${unit}, coolingSetpoint to ${coolTemp}°${unit}",2,null,'info')
    	stat.setProgramSetpoints( program, heatTemp, coolTemp )
    }
}

def getZipTemp() {
	return getTwcTemp('zip')
}

def getGPSTemp() {
	return getTwcTemp('gps')
}

def getPwsTemp() {
	return getWUTemp('pws')
}

// SmartThings-only
def getTwcTemp(type) {
	def isMetric = (getTemperatureScale() == "C")
	def source = (type == 'zip') ? settings.zipCode : ((type == 'gps')?"${location.latitude},${location.longitude}":null)
    
    def twcConditions = [:]
    try {
    	twcConditions = getTwcConditions(source)
    } catch (e) {
    	LOG("Error getting TWC Conditions: ${e}",1,null,'error')
        return null
    }
    if (twcConditions) {
    	LOG("Parsing TWC data",3,null,'info')
        def tempNow
    	def dewpointNow = -999.0
    	tempNow = twcConditions.temperature
        dewpointNow = twcConditions.temperatureDewPoint

        if (tempNow != null) {
        	if (dewpointNow != -999.0) {
        		atomicState.dewpoint = dewpointNow
            } else {
            	def hum = twcConditions.relativeHumidity
                if ((hum != null) && hum.contains('%')) hum = (hum-'%') as Integer		// strip off the trailing '%' sign
                if (hum.toString().isNumber()) {
                	dewpointNow = calculateDewpoint( tempNow, hum, (isMetric?'C':'F'))
                }
                atomicState.dewpoint = dewpointNow
            }
            LOG("Outside Dewpoint is: ${dewpointNow}°${isMetric?'C':'F'}",2,null,'info')
        	temperatureUpdate(tempNow)
            return tempNow
        } else {
        	LOG("Invalid temp returned ${newTemp}, ignoring...",2,null,'warn')
            return null
        }
    }
    LOG("Current conditions unavailable",1,null,'error')
    return null
}
// SmartThings only - deprecated
def getWUTemp(type) {
	def isMetric = (getTemperatureScale() == "C")
    def tempNow
    def dewpointNow
    def source = (type == 'zip') ? settings.zipCode : ((type == 'gps')?"${location.latitude},${location.longitude}":settings.stationID)
	Map wdata = getWeatherFeature('conditions', source)
    LOG("Requesting WU data for source: ${source}",3,null,'info')
    if (wdata && wdata.response) {
    	//LOG("conditions: ${wdata.response}",4,null,'trace')
		if (wdata.response.containsKey('error')) {
        	if (wdata.response.error.type != 'invalidfeature') {
    			LOG("Please check ${type=='zip'?'ZIPcode':((type=='gps')?'Location Lat/Lon':'WU Station')} setting, error:\n${wdata.response.error.type}: ${wdata.response.error.description}" ,1,null,'error')
        		return null
            } 
            else {
            	LOG("Error requesting weather:\n${wdata.response.error}",2,null,'warn')
            	return null
            }
		}
    }
    else {
    	LOG("Please check ZIPcode, Lat/Lon, or PWS setting, weather returned: null",2,null,'warn')
    	return null
    }
    if (wdata.current_observation) { 
    	LOG("Parsing WU data for station: ${wdata.current_observation.station_id}",3,null,'info')
    	if (!isMetric) {
			if (wdata.current_observation.temp_f?.isNumber()) tempNow = wdata.current_observation.temp_f.toBigDecimal()
            if (wdata.current_observation.dewpoint_f?.isNumber()) dewpointNow = wdata.current_observation.dewpoint_f.toBigDecimal()
        } else {
        	if (wdata.current_observation.temp_c?.isNumber()) tempNow = wdata.current_observation.temp_c.toBigDecimal()
            if (wdata.current_observation.dewpoint_c?.isNumber()) dewpointNow = wdata.current_observation.dewpoint_c.toBigDecimal()
        }
        if (tempNow?.isNumber()) {
        	if (dewpointNow != -999.0) {
        		atomicState.dewpoint = dewpointNow
            } else {
            	def hum = wdata.current_observation.relative_humidity
                if (hum && hum.contains('%')) hum = (hum-'%').toInteger()		// strip off the trailing '%' sign
                if (hum.isNumber()) {
                	dewpointNow = calculateDewpoint( tempNow, hum, (isMetric?'C':'F'))
                }
                atomicState.dewpoint = dewpointNow
            }
            LOG("Outside Dewpoint is: ${dewpointNow}°${isMetric?'C':'F'}",2,null,'info')
        	temperatureUpdate(tempNow)
            return tempNow
        } else {
        	LOG("Invalid temp returned ${newTemp}, ignoring...",2,null,'warn')
            return null
        }
    }
    LOG("Current conditions unavailable",1,null,'error')
    return null
}

// Calculate a close approximation of Dewpoint based on Temp, Relative Humidity (need Units - algorithm only works for C values)
def calculateDewpoint( temp, rh, units) {
	def t = ((units == 'C') ? temp : (temp-32)/1.8) as BigDecimal
	def dpC = 243.04*(Math.log(rh/100.0)+((17.625*t)/(243.04+t)))/(17.625-Math.log(rh/100.0)-((17.625*t)/(243.04+t)))
    return (units == 'C') ? roundIt(dpC, 2) : roundIt(((dpC*1.8)+32), 1)
}
def roundIt( value, decimals=0 ) {
	return (value == null) ? null : value.toBigDecimal().setScale(decimals, BigDecimal.ROUND_HALF_UP) 
}
def roundIt( BigDecimal value, decimals=0) {
    return (value == null) ? null : value.setScale(decimals, BigDecimal.ROUND_HALF_UP) 
}

// return all the modes that ALL thermostats support
def getThermostatModes() {
	def theModes = []
    
    settings.thermostats?.each { stat ->
    	if (theModes == []) {
        	theModes = stat.currentValue('supportedThermostatModes')[1..-2].tokenize(", ")
        } else {
        	theModes = theModes.intersect(stat.currentValue('supportedThermostatModes')[1..-2].tokenize(", "))
        }   
    }
    return theModes.sort(false)
}

// get the combined set of Ecobee Programs applicable for these thermostats
def getEcobeePrograms() {
	def programs
	if (thermostats?.size() > 0) {
		thermostats.each { stat ->
			def pl = stat.currentValue('programsList')
			if (!programs) {
				if (pl) programs = new JsonSlurper().parseText(pl)
			} else {
				if (pl) programs = programs.intersect(new JsonSlurper().parseText(pl))
			}
        }
	} 
	if (!programs) programs =  ['Away', 'Home', 'Sleep']
    LOG("getEcobeePrograms: returning ${programs}", 4, null, 'info')
    return programs.sort(false)
}

// return the external temperature range
def getThermostatRange() {
	def low
    def high
	if (getTemperatureScale() == "C") {
    	low = -20.0
        high = 65.0
    } else {
    	low = -5.0
		high = 150.0
    }
	return "${low}..${high}"
}

// returns the holdType keyword, OR the number of hours to hold
// precedence: 1. this SmartApp's preferences, 2. Parent settings.holdType, 3. indefinite (must specify to use the thermostat setting)
String whatHoldType(statDevice) {
    def theHoldType = settings.holdType
    def sendHoldType = null
    def parentHoldType = getParentSetting('holdType')
    if ((settings.holdType == null) || (settings.holdType == "Ecobee Manager Setting") || (settings.holdType == 'Parent Ecobee (Connect) Setting')) {
        if ((parentHoldType == null) || (parentHoldType == '')) {	// default for Ecobee (Connect) is permanent hold (legacy)
        	LOG('Using holdType indefinite',2,null,'info')
        	return 'indefinite'
        } else if (parentHoldType != 'Thermostat Setting') {
        	theHoldType = parentHoldType
        }
    }
    
    def parentHoldHours = getParentSetting('holdHours')
    switch (theHoldType) {
      	case 'Until I Change':
            sendHoldType = 'indefinite'
            break;   
        case 'Until Next Program':
           	sendHoldType = 'nextTransition'
            break;               
        case '2 Hours':
        	sendHoldType = 2
            break;
        case '4 Hours':
        	sendHoldType = 4
        case 'Specified Hours':
            if (settings.holdHours && settings.holdHours.isNumber()) {
            	sendHoldType = settings.holdHours
            } else if ((parentHoldType == 'Specified Hours') && (parentHoldHours != null)) {
            	sendHoldType = parentHoldHours
            } else if ( parentHoldType == '2 Hours') {
            	sendHoldType = 2
            } else if ( parentHoldType == '4 Hours') {
            	sendHoldType = 4            
            } else {
            	sendHoldType = 2
            }
            break;
        case 'Thermostat Setting':
       		String statHoldType = atomicState.isST ? statDevice.currentValue('statHoldAction') : statDevice.currentValue('statHoldAction', true)
            switch(statHoldType) {
            	case 'useEndTime4hour':
                	sendHoldType = 4
                    break;
                case 'useEndTime2hour':
                	sendHoldType = 2
                    break;
                case 'nextPeriod':
                case 'nextTransition':
                	sendHoldType = 'nextTransition'
                    break;
                case 'indefinite':
                case 'askMe':
                case null :
				case '':
                default :
                	sendHoldType = 'indefinite'
                    break;
           }
    }
    if (sendHoldType) {
    	LOG("Using holdType ${sendHoldType.isNumber()?'holdHours ('+sendHoldType.toString()+')':sendHoldType}",2,null,'info')
        return sendHoldType
    } else {
    	LOG("Couldn't determine holdType, returning indefinite",1,null,'error')
        return 'indefinite'
    }
}


def getHeatRange() {
	def low
    def high
    settings.thermostats.each { stat ->
    	def lo
        def hi
        def setp = stat.currentValue('heatRangeLow')
        lo = lo ? ((setp < lo) ? setp : lo) : setp
        setp = stat.currentValue('heatRangeHigh')
        hi = hi ? ((setp > hi) ? setp : hi) : setp
        // if there are multiple stats, we need to find the range that ALL stats can support
        low = low ? ((lo > low) ? lo : low) : lo
        high = high ? ((hi < high) ? hi : high) : hi
    }
    return "${roundIt(low-0.5,0)}..${roundIt(high-0.5,0)}"
}

def getCoolRange() {
	def low
    def high
    settings.thermostats.each { stat ->
    	def lo
        def hi
        def setp = stat.currentValue('coolRangeLow')
        lo = lo ? ((setp < lo) ? setp : lo) : setp
        setp = stat.currentValue('coolRangeHigh')
        hi = hi ? ((setp > hi) ? setp : hi) : setp
        // if there are multiple stats, we need to find the range that ALL stats can support
        low = low ? ((lo > low) ? lo : low) : lo
        high = high ? ((hi < high) ? hi : high) : hi
    }
    return "${roundIt(low-0.5,0)}..${roundIt(high-0.5,0)}"
}

String getZIPcode() {
	return location.zipCode ?: ""
}

String getPWSID() {
	String PWSID = location.zipCode
	LOG("Location ZIP Code ${PWSID}", 3, null, 'debug')
	// find the nearest PWS to the hub's geo location
	String geoLocation = location.zipCode
	// use coordinates, if available
	if (location.latitude && location.longitude) geoLocation = "${location.latitude},${location.longitude}"
    LOG("Geolocation: ${geoLocation}", 3, null, 'debug')
    Map wdata = getWeatherFeature('geolookup', geoLocation)
    if (wdata && wdata.response && !wdata.response.containsKey('error')) {	// if we get good data
    	if (wdata.response.features.containsKey('geolookup') && (wdata.response.features.geolookup.toInteger() == 1) && wdata.location) {
        	//log.debug "wdata ${wdata.location.nearby_weather_stations.pws}"
            LOG("wdata ${wdata.location.nearby_weather_stations}", 3, null, 'debug')
    		if (wdata.location.nearby_weather_stations?.pws?.station[0]?.id) PWSID = 'pws:' + wdata.location.nearby_weather_stations.pws.station[0].id
            else if (wdata.location.nearby_weather_stations?.airport?.station[0]?.icao) PWSID = wdata.location.nearby_weather_stations.airport.station[0].icao
    	}
    	else LOG("bad response", 3, null, 'debug')
    }
    else LOG("null or error", 3, null, 'debug')

	LOG("Nearest PWS ${PWSID}", 3, null, 'info')
	return PWSID
}

def getDeviceId(networkId) {
    return networkId.split(/\./).last() as String
}

// Reservation Management Functions - Now implemented in Ecobee Suite Manager
void makeReservation(String tid, String type='modeOff' ) {
	parent.makeReservation( tid, app.id as String, type )
}
// Cancel my reservation
void cancelReservation(String tid, String type='modeOff') {
	//log.debug "cancel ${tid}, ${type}"
	parent.cancelReservation( tid, app.id as String, type )
}
// Do I have a reservation?
boolean haveReservation(String tid, String type='modeOff') {
	return parent.haveReservation( tid, app.id as String, type )
}
// Do any Apps have reservations?
boolean anyReservations(String tid, String type='modeOff') {
	return parent.anyReservations( tid, type )
}
// How many apps have reservations?
Integer countReservations(String tid, String type='modeOff') {
	return parent.countReservations( tid, type )
}
// Get the list of app IDs that have reservations
List getReservations(String tid, String type='modeOff') {
	return parent.getReservations( tid, type )
}
// Get the list of app Names that have reservations
List getGuestList(String tid, String type='modeOff') {
	return parent.getGuestList( tid, type )
}

// Helper Functions
void LOG(message, level=3, child=null, logType="debug", event=true, displayEvent=true) {
	String msg = "${atomicState.appDisplayName} ${message}"
    if (logType == null) logType = 'debug'
	if ((logType != 'debug') || (!settings.debugOff)) log."${logType}" message
	parent.LOG(msg, level, null, logType, event, displayEvent)
}

void sendMessage(notificationMessage) {
	LOG("Notification Message (notify=${notify}): ${notificationMessage}", 2, null, "trace")
	boolean ST = atomicState.isST
    if (settings.notify) {
		String msg = "${atomicState.appDisplayName} at ${location.name}: " + notificationMessage		// for those that have multiple locations, tell them where we are
		if (ST) {
			if (settings.notifiers != null) {
				settings.notifiers.each {									// Use notification devices (if any)
					it.deviceNotification(msg)
				}
			}
			if (settings.phone) { // check that the user did select a phone number
				if ( settings.phone.indexOf(";") > 0){
					def phones = settings.phone.split(";")
					for ( def i = 0; i < phones.size(); i++) {
						LOG("Sending SMS ${i+1} to ${phones[i]}", 3, null, 'info')
						sendSmsMessage(phones[i].trim(), msg)				// Only to SMS contact
					}
				} else {
					LOG("Sending SMS to ${settings.phone}", 3, null, 'info')
					sendSmsMessage(settings.phone.trim(), msg)				// Only to SMS contact
				}
			} 
			if (settings.pushNotify) {
				LOG("Sending Push to everyone", 3, null, 'warn')
				sendPushMessage(msg)										// Push to everyone
			}
			if (settings.speak) {
				if (settings.speechDevices != null) {
					settings.speechDevices.each {
						it.speak( "From " + msg )
					}
				}
				if (settings.musicDevices != null) {
					settings.musicDevices.each {
						it.setLevel( settings.volume )
						it.playText( "From " + msg )
					}
				}
			}
		} else {		// isHE
			if (settings.notifiers != null) {
				settings.notifiers.each {							// Use notification devices on Hubitat
					it.deviceNotification(msg)
				}
			}
			if (settings.phone != null) {
				if ( settings.phone.indexOf(",") > 0){
					def phones = phone.split(",")
					for ( def i = 0; i < phones.size(); i++) {
						LOG("Sending SMS ${i+1} to ${phones[i]}", 3, null, 'info')
						sendSmsMessage(phones[i].trim(), msg)				// Only to SMS contact
					}
				} else {
					LOG("Sending SMS to ${settings.phone}", 3, null, 'info')
					sendSmsMessage(settings.phone.trim(), msg)						// Only to SMS contact
				}
			}
			if (settings.speak) {
				if (settings.speechDevices != null) {
					settings.speechDevices.each {
						it.speak( "From " + msg )
					}
				}
				if (settings.musicDevices != null) {
					settings.musicDevices.each {
						it.setLevel( settings.volume )
						it.playText( "From " + msg )
					}
				}
			}
			
		}
    }
	// Always send to Hello Home / Location Event log
	if (ST) { 
		sendNotificationEvent( notificationMessage )					
	} else {
		sendLocationEvent(name: "HelloHome", descriptionText: notificationMessage, value: app.label, type: 'APP_NOTIFICATION')
	}
}

void updateMyLabel() {
	boolean ST = atomicState.isST
	def opts = [' (pa', '(Co', ' (He', ' (Au', ' (Of', ' (Au', ' (Em']
	String flag
	if (ST) {
		opts.each {
			if (!flag && app.label.contains(it)) flag = it
		}
	} else {
		flag = '<span '
	}
	
	// Display Ecobee connection status as part of the label...
	String myLabel = atomicState.appDisplayName
	if ((myLabel == null) || !app.label.startsWith(myLabel)) {
		myLabel = app.label
		if (!myLabel.contains(flag)) atomicState.appDisplayName = myLabel
	} 
	if (flag && myLabel.contains(flag)) {
		// strip off any connection status tag
		myLabel = myLabel.substring(0, myLabel.indexOf(flag))
		atomicState.appDisplayName = myLabel
	}
	if (settings.tempDisable) {
		String  newLabel = myLabel + (!ST ? '<span style="color:red"> (paused)</span>' : ' (paused)')
		if (app.label != newLabel) app.updateLabel(newLabel)
	} else {
		String modeProgStr = ' (' + thermostats[0].currentValue('thermostatMode').capitalize() + ' - ' + thermostats[0].currentValue('currentProgramName') + ')'
		String newLabel = myLabel + (!ST ? '<span style="color:green">' + modeProgStr : modeProgStr)					 
		if (app.label != newLabel) app.updateLabel(newLabel)
	}
}
def pauseOn() {
	// Pause this Helper
	atomicState.wasAlreadyPaused = (settings.tempDisable && !atomicState.globalPause)
	if (!settings.tempDisable) {
		LOG("performing Global Pause",2,null,'info')
		app.updateSetting("tempDisable", true)
		atomicState.globalPause = true
		runIn(2, updated, [overwrite: true])
	} else {
		LOG("was already paused, ignoring Global Pause",3,null,'info')
	}
}
def pauseOff() {
	// Un-pause this Helper
	if (settings.tempDisable) {
		def wasAlreadyPaused = atomicState.wasAlreadyPaused
		if (!wasAlreadyPaused) { // && settings.tempDisable) {
			LOG("performing Global Unpause",2,null,'info')
			app.updateSetting("tempDisable", false)
			runIn(2, updated, [overwrite: true])
		} else {
			LOG("was paused before Global Pause, ignoring Global Unpause",3,null,'info')
		}
	} else {
		LOG("was already unpaused, skipping Global Unpause",3,null,'info')
		atomicState.wasAlreadyPaused = false
	}
	atomicState.globalPause = false
}

// SmartThings/Hubitat Portability Library (SHPL)
// Copyright (c) 2019, Barry A. Burke (storageanarchy@gmail.com)
String  getPlatform() { return (physicalgraph?.device?.HubAction ? 'SmartThings' : 'Hubitat') }	// if (platform == 'SmartThings') ...
boolean getIsST()     { return (atomicState?.isST != null) ? atomicState.isST : (physicalgraph?.device?.HubAction ? true : false) }					// if (isST) ...
boolean getIsHE()     { return (atomicState?.isHE != null) ? atomicState.isHE : (hubitat?.device?.HubAction ? true : false) }						// if (isHE) ...

String getHubPlatform() {
	def pf = getPlatform()
    atomicState?.hubPlatform = pf			// if (atomicState.hubPlatform == 'Hubitat') ... 
											// or if (state.hubPlatform == 'SmartThings')...
    atomicState?.isST = pf.startsWith('S')	// if (atomicState.isST) ...
    atomicState?.isHE = pf.startsWith('H')	// if (atomicState.isHE) ...
    return pf
}
boolean getIsSTHub() { return atomicState.isST }					// if (isSTHub) ...
boolean getIsHEHub() { return atomicState.isHE }					// if (isHEHub) ...

def getParentSetting(String settingName) {
	return isST ? parent?.settings?."${settingName}" : parent?."${settingName}"	
}
