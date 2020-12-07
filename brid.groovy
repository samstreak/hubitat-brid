/*
* BRID Air Purifier
*
* Description:
* This Hubitat driver polls the status page provided by a BRID Air Purifier and
* then reads the current values of selected sensors so that they can be used by Hubitat Rules or other
* purposes. 
*
* Features List:
* v0.1 - initial version modified from David Snell's Neptune Systems Apex driver
* v0.1b - added all available refresh rate intervals as per Hubitat documentation https://docs.hubitat.com/index.php?title=Common_Methods_Object#Additional_to_be_documented
*         Changed TempScale to enum with C/F as options
*         Added check to only log changes in status
*         Moved mode logging text into main function vice individual functions
*         Removed isStateChange: true   from all sendEvents to reduce logging
* v0.1c - Changed sendStatusRequest to use switch instead of several if statements
* v0.1d - Updated to newer version JSON data - added MAC Address to info page and light level name changes to main status, updated setlights function to new nomenclature "PS", removed fanlevel per new JSON
*
*
*
* To Do:  Add filter percentage?  from status data.Filters."HC filter" and "NWF filter"
*
* Licensing:
* Copyright 2019 Jason Moore
* Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
* in compliance with the License. You may obtain a copy of the License at: http://www.apache.org/licenses/LICENSE-2.0
* Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
* on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
* for the specific language governing permissions and limitations under the License.
*
* Version Control:
* 0.1 - Initial inspiration/learning from David Snell's Neptune Systems Apex driver, in turn based on @ogiewon's (Daniel Ogorchock) HTTP Momentary Switch & presence code from
*       @joelwetzel's (Joel Wetzel) Hubitat-HTTP-Presence-Sensor
*/
def version="0.1d"

metadata{
	definition ( name: "BRID Air Purifier", namespace: "samstreak", author: "Jason Moore" ) {
		capability "Sensor"
		capability "Refresh"
		capability "Temperature Measurement"
        capability "RelativeHumidityMeasurement"
		//capability "Air Quality Measurement"???  need to figure out what to use here
        capability "Switch"
        capability "Actuator"
        
		// Attributes being built into the device
		attribute "Temperature", "FLOAT"
        attribute "TempScale", "STRING"
		attribute "Humidity", "FLOAT"
        attribute "Mode", "INTEGER"
        attribute "ModeState", "STRING"
		attribute "LightLevel", "INTEGER"
//        attribute "FanLevel", "INTEGER"
		attribute "Firmware", "STRING"
        attribute "Serial", "STRING"
        attribute "MACADDR", "STRING"

     
        command "Normal"
        command "Boost"
        command "Night"
        command "LightsOff"
        command "LightsOn"
        command "TurnOff"
        command "Configure"
	}
	preferences{
		section{
			input( type: "enum", name: "RefreshRate", title: "Data Refresh Rate (in minutes)", required: true, multiple: false, options: [ [ "1" : "1" ],[ "5" : "5" ], [ "10" : "10" ],[ "15" : "15"], [ "30" : "30" ], [ "60" : "60" ], [ "180" : "180" ] ], defaultValue: "1" )
			input( type: "enum", name: "LogType", title: "Enable Logging?", required: true, multiple: false, options: [ [ "1" : "None" ], [ "2" : "Info" ], [ "3" : "Debug" ] ], defaultValue: "2" )
            input( type: "enum", name: "TempScale", title: "Temperature Scale", required: true, multiple: false, options: [ "c", "f" ], defaultValue: "c" )

		}
	}
}

def Configure(hostAddress) {
    state.hostAddress = device.getDeviceNetworkId()
    sendEvent(name: "hostAddress", value: state.hostAddress, displayed: false)
    
    Logging( "Configured hostAddress ${ hostAddress }", 3 )
}

private sendStatusRequest() {   
    def statusParams = [
        uri: "http://${state.hostAddress}/status",
        body: ""
    ]
    
    Logging ("[${device.name}]: requesting device status...", 3 )
    
    httpGet(statusParams) { resp ->
        Logging ("[${device.name}] response: ${resp.data}", 3 )
        
        //Sensors section
        def Sensors = resp.data.Sensors
        sensTemp = Sensors.Temperature
        tTemperature = Float.parseFloat( "${sensTemp}" )
		
        if ( tTemperature != state.Temperature ) {
          Logging( "Temperature = ${ tTemperature }°${ TempScale }", 3)
		  state.Temperature = tTemperature
          //As a note... the name of the send event for temperature MUST be lowercase... or else it does not work in rules or the dashboard
    	  sendEvent( name: "temperature", value: tTemperature, unit: TempScale, linkText: deviceName, descriptionText: "" )
        }
        
        sensHum = Sensors.Humidity
        tHumidity = Float.parseFloat( "${sensHum}" )
        
        if ( tHumidity != state.Humidity ) {
            Logging( "Humidity = ${ tHumidity }%", 3)
            state.Humidity = tHumidity
            sendEvent( name: "humidity", value: tHumidity, linkText: deviceName, descriptionText: "" )
        }
        
        
        //Settings section
        def Settings = resp.data.Settings
        //settsLight = Settings.Light
        settsLight = Settings.Photocatalysis
        tLightLevel = Integer.parseInt( "${settsLight}" )
        
        if ( tLightLevel != state.LightLevel ) {
            Logging( "Light level = ${tLightLevel}", 3)
            state.LightLevel = tLightLevel
            sendEvent( name: "LightLevel", value: tLightLevel, linkText: deviceName, descriptionText: "" )
        }
        
//        settsFan = Settings.Fan
//        tFanLevel = Integer.parseInt( "${settsFan}" )
        
//        if ( tFanLevel != state.FanLevel ) {
//            Logging( "Fan level = ${tFanLevel}", 3)
//            state.FanLevel = tFanLevel
//            sendEvent( name: "FanLevel", value: tFanLevel, linkText: deviceName, descriptionText: "")
//        }
        
        settsMode = Settings.Mode
        tMode = Integer.parseInt( "${settsMode}" )
        
        def modeText = "" as String
        switch (tMode) {
            case 0:
                modeText = "Off";
                break;
            case 2:
                modeText = "Normal";
                break;
            case 3:
                modeText = "Boost";
                break;
            case 4:
                modeText = "Night";
                break;
        }
        
        if ( tMode != Integer.parseInt(state.Mode)  ) {
            Logging( "Mode = ${modeText} (${tMode})", 3)
            state.Mode = "${tMode}"
            state.ModeState = "${modeText}"
            sendEvent( name: "Mode", value: tMode, linkText: deviceName, descriptionText: "")
            sendEvent( name: "ModeState", value: modeText, linkText: deviceName, descriptionText: "")
        }
    }
    
}


def LightsOff(){
  //def light_Off = 1
  
  //setLights(light_Off)
  setLights(0)
}
def LightsOn(){
  //def light_Off = 0
  
  //setLights(light_Off)
  setLights(1)
}

private setLights(onOff){
    def Params = [
        uri: "http://${state.hostAddress}/settings",
        contentType: "application/json",
        requestContentType: "application/json",
//        body: "{\"light_off\": ${onOff}}"
        body: "{\"ps\": ${onOff}}"
    ]
    
    httpPost(Params) { resp ->
        if (resp.status != 200) {log.debug resp.status} else {
          Logging( "Response: ${resp.data}", 3 )
        
          def rtn = resp.data.success
        
          if (rtn == 0) {
            refresh()
          }
       }
    }
}


def Night(){
    setMode(4)
    LightsOff()
}
def Boost(){
    setMode(3)
    LightsOn()
}
def Normal(){
    setMode(2)
    LightsOn()
}
def TurnOff(){    
    setMode(0)
}

def off(){TurnOff()}
def on(){Normal()}

private setMode(mode) { 
    def Params = [
        uri: "http://${state.hostAddress}/settings",
        contentType: "application/json",
        requestContentType: "application/json",
        body: "{\"mode\": ${mode}}"
    ]
    
    switch (mode) {
        case 0:
            modeText = "Off";
            break;
        case 2:
            modeText = "Normal";
            break;
        case 3:
            modeText = "Boost";
            break;
        case 4:
            modeText = "Night";
            break;
    }
    
    Logging( "[${device.name}]: setting mode: ${ modeText }", 3 )
    
    httpPost(Params) { resp ->
        if (resp.status != 200) {log.debug resp.status} else {
          Logging( "Response: ${resp.data}", 3 )
        
          def rtn = resp.data.success
        
          if (rtn == 0) {
            refresh()
          }
       }
    }
}


private sendInfoRequest() {
    Logging( "[${device.name}]: requesting device basic info...", 3 )
    
    def infoParams = [
        uri: "http://${state.hostAddress}/info",
        body: ""
    ]
    
    httpGet(infoParams){ resp ->
        Logging( "Response: ${resp.data}", 3 )
        
        def Model = resp.data.Model
        def SerNum = resp.data."Serial Number"
        def Mfg = resp.data.Manufacturer
        def Version = resp.data.Version
        def MAC_Addr = resp.data."MAC Address"
        
    	Logging( "Firmware Version: ${ Version }", 3 )
   	    state.Firmware = "${ Version }"   
    
        //Logging( "Serial#: ${ SerNum }", 3)
        state.SerialNo = "${ SerNum }"
        
        state.MACADDR = "${ MAC_Addr }"

    }
}

def refresh(){
  sendStatusRequest()
}

// updated is called whenever device parameters are saved
// It sets the current version of the driver and sets some basic settings
def updated(){
    state.Mode="0"
	Logging( "BRID Air Purifier: Updated", 2 )
    state.hostAddress = device.getDeviceNetworkId()
    
	sendInfoRequest()
	// Check what the refresh rate is set for then run it
    switch ( RefreshRate ) {
      case "1":
		runEvery1Minute( refresh );
    	runIn( 1, refresh );
		Logging( "Refresh rate: 1 minute", 3 );
        break;
	  case "5":
		runEvery5Minutes( refresh );
		runIn( 5, refresh );
		Logging( "Refresh rate: 5 minutes", 3 );
        break;
	  case "10":
		runEvery10Minutes( refresh );
		runIn( 10, refresh );
		Logging( "Refresh rate: 10 minutes", 3 );
        break;
      case "15":
        runEvery15Minutes( refresh );
		runIn( 15, refresh );
		Logging( "Refresh rate: 15 minutes", 3 );
        break;
      case "30":
        runEvery30Minutes( refresh );
		runIn( 30, refresh );
		Logging( "Refresh rate: 30 minutes", 3 );
        break;
      case "60":
        runEvery1Hour( refresh );
		runIn( 60, refresh );
		Logging( "Refresh rate: 1 hour", 3 );
        break;
      case "180":
        runEvery3Hours( refresh );
		runIn( 180, refresh );
		Logging( "Refresh rate: 3 hours", 3 );
        break;
	}
}


// installed is called when the device is installed, all it really does is run updated
def installed(){
	Logging( "Installed", 2 )
	updated()
}

// initialize is called when the device is initialized, all it really does is run updated
def initialize(){
	Logging( "Initialized", 2 )
	updated()
}

// Handles whether logging is enabled and thus what to put there.
def Logging( LogMessage, LogLevel ){
	// Add all messages as info logging
	if( LogType == "2" && LogLevel <= 2 ){
		log.info( "${ device.displayName } - ${ LogMessage }" )
	}
	// Add all messages as debug logging
	if( LogType == "3" && LogLevel <= 3 ){
		log.debug( "${ device.displayName } - ${ LogMessage }" )
	}
}
