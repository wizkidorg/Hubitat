/**
 *  Hubigraph HeatMap Child App
 *
 *  Copyright 2020, but let's behonest, you'll copy it
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
 */

// Hubigraph Heat Map Changelog
// V 1.0 Intial release

import groovy.json.JsonOutput

def ignoredEvents() { return [ 'lastReceive' , 'reachable' , 
                         'buttonReleased' , 'buttonPressed', 'lastCheckinDate', 'lastCheckin', 'buttonHeld' ] }

def version() { return "v0.22" }

definition(
    name: "Hubigraph Heat Map",
    namespace: "wizkidorg",
    author: "wizkidorg",
    description: "Hubigraph Heat Map",
    category: "",
    parent: "wizkidorg:Hubigraphs",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
)


preferences {
       page(name: "mainPage", install: true, uninstall: true)
       page(name: "deviceSelectionPage", nextPage: "attributeConfigurationPage")
       page(name: "attributeConfigurationPage", nextPage: "mainPage")
       page(name: "graphSetupPage", nextPage: "mainPage")
       page(name: "enableAPIPage")
       page(name: "disableAPIPage") 

mappings {
    path("/graph) {
            action: [
              GET: "getGraph"
            ]
        }
    }
    
    path("/graph/getData/") {
        action: [
            GET: "getData"
        ]
    }
        
    path("/graph/getOptions/") {
        action: [
            GET: "getOptions"
        ]
    }
    
    path("/graph/getSubscriptions/") {
        action: [
            GET: "getSubscriptions"
        ]
    }
}

def call(Closure code) {
    code.setResolveStrategy(Closure.DELEGATE_ONLY);
    code.setDelegate(this);
    code.call();
}

def getAttributeType(attrib, title){
    
    switch (attrib){
         case "motion":         return ["motion", "Motion (active/inactive)"];
         case "switch":         return ["switch", "Switch (on/off)"];
         case "contact":        return ["contact", "Contact (open/close)"];
         case "acceleration":   return ["acceleration", "Acceleration (active/inactive)"]
         case "audioVolume":
         case "number": return [title, "Number (Choose threshold)"];  
    }
}

def getFilterName(filter){
    switch (filter){
         case "capability.*": return "Sensor";
         case "capability.temperatureMeasurement": return "Temperature";
         case "capability.relativeHumidityMeasurement": return "Humidity";
         case "capability.battery": return "Battery";
         case "capability.motionSensor": return "Motion";
         case "capability.contactSensor": return "Contact";
         case "capability.switch": return "Switch";
    }
    
}

def deviceSelectionPage() {                  
    def final_attrs;
    
    filterText = "capability.*";
    
    def filterEnum = [["capability.*":                             "All Capabilities"], 
                      ["capability.temperatureMeasurement":        "Temperature"], 
                      ["capability.relativeHumidityMeasurement":   "Humidity"], 
                      ["capability.battery":                       "Battery"],
                      ["capability.motionSensor":                  "Motion"],
                      ["capability.contactSensor":                 "Contact"],
                      ["capability.switch":                        "Switch"],
                      
                     ];
    
    def fillEnum = [["default":      "Select to Fill...."],
                    ["temperature":  "Temperature"],
                    ["humidity":     "Humidity"],
                    ["battery":      "Battery"],
                    ["motion":       "Motion"],
                    ["contact" :     "Contact"],
                    ["switch":       "Switch"],
                    ["lastupdate":  "Last Update"],
                   ];
    
    dynamicPage(name: "deviceSelectionPage") {
        
         parent.hubiForm_section(this,"Attribute Filter", 1){
             input( type: "enum", name: "filter", title: "Attributes Filter", required: true, multiple: false, options: filterEnum, defaultValue: "All Capabilities", submitOnChange: true)
         }
        
         parent.hubiForm_section(this,"Device Selection", 1){
        
              
            input "sensors", filter, title: getFilterName(filter)+" Devices", multiple: true, required: true, submitOnChange: true
            //input "sensors", "capability.temperatureMeasurement", title: getFilterName(filter)+" Devices", multiple: true, required: true, submitOnChange: true
             
            if (sensors){
                def restValue;
                
                resetValue = fill_value ? fill_value : "default";
                if (resetValue != "default") {
                    app.updateSetting ("fill_value", ["default"]);
                }
                                
                input( type: "enum", name: "fill_value", title: "<b>Auto Fill Value</b><br><small>Selecting will cause page to refresh with selected value filled in below</small>", multiple: false, required: false, options: fillEnum, defaultValue: "default", submitOnChange:true)
                
                sensors.each {
                    attributes_ = it.getSupportedAttributes();
                    final_attrs = [];
                    
                    attributes_.each{ attribute_->
                        name = attribute_.getName();
                        if (it.currentState(name)){
                            final_attrs << ["$name" : "$name ::: [${it.currentState(name).getValue()}]"];
                        }
                        
                    }
                    final_attrs = final_attrs.unique(false);
                    final_attrs << ["lastupdate": "last activity ::: [${it.getLastActivity()}]"];
                    
                    container = [];
                    container <<  parent.hubiForm_sub_section(this, it.displayName);
                    parent.hubiForm_container(this, container, 1);  
                    default_ = getFilterName(filter).toLowerCase();
                    if (resetValue!="default") {
                        app.updateSetting ("attributes_${it.id}", [resetValue]);
                    }
                    input( type: "enum", name: "attributes_${it.id}", title: "Attributes to graph", required: true, multiple: true, options: final_attrs, defaultValue: default_);  
                }
            }
        }
    }
}

def attributeConfigurationPage() {   
    
    dynamicPage(name: "attributeConfigurationPage") {
         parent.hubiForm_section(this, "Directions", 1, "directions"){
            container = [];
            container << parent.hubiForm_text(this, "Choose Numeric Attributes Only");
            parent.hubiForm_container(this, container, 1); 

         }
        
         parent.hubiForm_section(this, "Graph Order", 1, "directions"){
             parent.hubiForm_list_reorder(this, "graph_order", "background");       
         }
         
          sensors.each { sensor ->
             attributes = settings["attributes_${sensor.id}"];
             attributes.each { attribute ->
                 container = [];
                 parent.hubiForm_section(this, "${sensor.displayName} ${attribute}", 1, "directions"){
                     container << parent.hubiForm_text_input(this,   "<small></i>Use %deviceName% for DEVICE and %attributeName% for ATTRIBUTE</i></small>",
                                                                     "graph_name_override_${sensor.id}_${attribute}",
                                                                     "%deviceName%: %attributeName%", false);
                     parent.hubiForm_container(this, container, 1); 
                 }
             }
          }
        
        
    }
}
def dd(num){
    if (num<10) return "0"+num.toInteger();
    else return num.toInteger();
}

def convertToString(msec_){
    def msec = msec_.toInteger();
    if (msec == "0" || msec == 0) return "00:00:00";
    
    def hours = Math.floor(msec/3600000);
    def mins =  Math.floor((msec%3600000)/60000);
    def secs =  Math.floor((msec%60000)/1000);
    
    return dd(hours)+":"+dd(mins)+":"+dd(secs);
}

def graphSetupPage(){
    
    def rateEnum = [["-1":"Never"], ["0":"Real Time"], ["10":"10 Milliseconds"], ["1000":"1 Second"], ["5000":"5 Seconds"], ["60000":"1 Minute"], 
                    ["300000":"5 Minutes"], ["600000":"10 Minutes"], ["1800000":"Half Hour"], ["3600000":"1 Hour"]];
    
    def decayEnum = [["1000":"1 Second"],     ["30000":"30 Seconds"], ["60000":"1 Minute"],  ["300000":"5 Minutes"], ["600000":"10 Minutes"], 
                       ["1800000":"Half Hour"], ["3600000":"1 Hour"],   ["7200000":"2 Hours"], ["21600000":"6 Hours"], ["43200000":"12 Hours"], ["86400000":"1 Day"],
                       ["172800000":"2 Days"], ["259200000":"3 Days"], ["345600000":"4 Days"], ["432000000":"5 Days"], ["518400000":"6 Days"], ["604800000":"7 Days"]];
    
    def timespanEnum = [[0:"Live"], [1:"Hourly"], [2:"Daily"], [3:"Every Three Days"], [4:"Weekly"]];
    
    def typeEnum = [["value": "Value"], ["time" : "Trigger (Time Since Last Update)"]];
    
    def count_ = 0;
    //Get Device Count
    sensors.each { sensor ->
             attributes = settings["attributes_${sensor.id}"];
             attributes.each { attribute ->
                 count_++;
             }
    }
    app.updateSetting ("attribute_count", count_);
    
    dynamicPage(name: "graphSetupPage") {
        parent.hubiForm_section(this, "General Options", 1){
            
            container = [];
            input( type: "enum", name: "graph_update_rate", title: "<b>Select graph update rate</b>", multiple: false, required: false, options: rateEnum, defaultValue: "0");
            input( type: "enum", name: "graph_type",        title: "<b>Select Graph Type</b>", multiple: false, required: false, options: typeEnum, defaultValue: "value", submitOnChange: true);
            
            if (!graph_type) graph_type = "value";
            if (graph_type == "time"){
                input( type: "enum", name: "graph_decay", title: "<b>Decay Rate</b>", multiple: false, required: false, options: decayEnum, defaultValue: "300000", submitOnChange: true);   
            }
    
            container << parent.hubiForm_color (this, "Graph Background", "graph_background", "#FFFFFF", false)
            container << parent.hubiForm_color (this, "Graph Line", "graph_line", "#000000", false)
            container << parent.hubiForm_line_size (this, title: "Graph Line", 
                                                          name: "graph",  
                                                          default: 2, 
                                                          min: 1, 
                                                          max: count_, 
                                                     );
            
            parent.hubiForm_container(this, container, 1);   
        }

        if (graph_num_gradients == null){
             settings["graph_num_gradients"] = 2;    
             num_ = 2;
        } else {
            num_ = graph_num_gradients.toInteger();   
        }
        parent.hubiForm_section(this, "Level Gradient", 1){
            
            container = [];
            
            container << parent.hubiForm_text_input(this,   "Number of Gradient Levels",
                                                            "graph_num_gradients",
                                                             2,
                                                             "true");
                        
            if (graph_type == "value"){
                
                for (gradient = 0; gradient < num_; gradient++){
                    subcontainer = [];
                    if (gradient == 0) titleString = "Start"
                    else if (gradient == num_-1) titleString = "End"
                    else titleString = "Mid"
                
                    subcontainer << parent.hubiForm_text_input(this, titleString+" Value",
                                                                 "graph_gradient_${gradient}_value",
                                                                  gradient*10,
                                                                  false);
                
                    subcontainer << parent.hubiForm_color     (this, "Gradient #"+gradient, 
                                                                 "graph_gradient_${gradient}",  
                                                                  parent.hubiTools_rotating_colors(gradient), 
                                                                  false);
                                                        
                    container << parent.hubiForm_subcontainer(this, objects: subcontainer, 
                                                                breakdown: [0.25, 0.75]);   
                                                         
                }                                                
            } else {
                def add_time = (graph_decay.toInteger()/(graph_num_gradients.toInteger()-1));
                def curr_time = 0;
                for (gradient = 0; gradient < num_; gradient++){
                    subcontainer = [];
                    
                    subcontainer << parent.hubiForm_text_format(this, text: convertToString(curr_time),
                                                                      horizontal_align: "right",
                                                                      vertical_align: "20px",
                                                                      size: 24,
                                                               );
                    
                                                         
                    app.updateSetting ("graph_gradient_${gradient}_value", curr_time);
                
                    subcontainer << parent.hubiForm_color     (this, "Gradient #"+gradient, 
                                                                 "graph_gradient_${gradient}",  
                                                                  parent.hubiTools_rotating_colors(gradient), 
                                                                  false);
                                                        
                    container << parent.hubiForm_subcontainer(this, objects: subcontainer, 
                                                                breakdown: [0.25, 0.75]);  
                    
                    curr_time += add_time;
                    
                }
                
                
            }
            parent.hubiForm_container(this, container, 1);   
        }
        
        parent.hubiForm_section(this, "Graph Size", 1){
            container = [];
            default_ = Math.ceil(Math.sqrt(count_)).intValue();
            cols = graph_num_columns ? graph_num_columns : default_;
            rows = Math.ceil(count_/cols).intValue();
            container << parent.hubiForm_slider (this, title: "Number of Columns<br><small>"+count_+" Devices/Attributes -- "+cols+" X "+rows+"</small>", 
                                                       name: "graph_num_columns",  
                                                       default: default_, 
                                                       min: 1, 
                                                       max: count_, 
                                                       units: " columns",
                                                       submit_on_change: true);

            
            input( type: "bool", name: "graph_static_size", title: "<b>Set size of Graph?</b><br><small>(False = Fill Window)</small>", defaultValue: false, submitOnChange: true);
            if (graph_static_size==true){      
                container << parent.hubiForm_slider (this, title: "Horizontal dimension of the graph", name: "graph_h_size",  default: 800, min: 100, max: 3000, units: " pixels");
                container << parent.hubiForm_slider (this, title: "Vertical dimension of the graph", name: "graph_v_size",  default: 600, min: 100, max: 3000, units: " pixels");   
            }

            parent.hubiForm_container(this, container, 1); 
        }
        parent.hubiForm_section(this, "Annotations", 1){
            container = [];
            container << parent.hubiForm_switch(this, title: "Show values inside Heat Map?", name: "show_annotations", default: false, submit_on_change: true);
            if (show_annotations==true){
                container << parent.hubiForm_font_size (this, title: "Annotation", name: "annotation", default: 16, min: 2, max: 40);
                container << parent.hubiForm_color     (this, "Annotation", "annotation",  "#FFFFFF", false);
                container << parent.hubiForm_color     (this, "Annotation Aura", "annotation_aura", "#000000", false);
                container << parent.hubiForm_slider    (this, title: "Number Decimal Places", name: "graph_decimals",  default: 1, min: 0, max: 4, units: " decimal places"); 
                container << parent.hubiForm_switch    (this, title: "Bold Annotation", name: "annotation_bold", default:false);
                container << parent.hubiForm_switch    (this, title: "Italic Annotation", name: "annotation_bold", default:false);
            }
            parent.hubiForm_container(this, container, 1); 
        }            
        
    }
}

def disableAPIPage() {
    dynamicPage(name: "disableAPIPage") {
        section() {
            if (state.endpoint) {
                try {
                   revokeAccessToken();
                }
                catch (e) {
                    log.debug "Unable to revoke access token: $e"
                }
                state.endpoint = null
            }
            paragraph "It has been done. Your token has been REVOKED. Tap Done to continue."
        }
    }
}

def enableAPIPage() {
    dynamicPage(name: "enableAPIPage", title: "") {
        section() {
            if(!state.endpoint) initializeAppEndpoint();
            if (!state.endpoint){
                paragraph "Endpoint creation failed"
            } else {
                paragraph "It has been done. Your token has been CREATED. Tap Done to continue."
            }
        }
    }
}

def mainPage() {
    dynamicPage(name: "mainPage") {        
       
            def container = [];
            if (!state.endpoint) {
                parent.hubiForm_section(this, "Please set up OAuth API", 1, "report"){
                    href name: "enableAPIPageLink", title: "Enable API", description: "", page: "enableAPIPage"    
                 }    
            } else {
               parent.hubiForm_section(this, "Graph Options", 1, "tune"){
                    container = [];
                    container << parent.hubiForm_page_button(this, "Select Device/Data", "deviceSelectionPage", "100%", "vibration");
                    container << parent.hubiForm_page_button(this, "Configure Graph", "graphSetupPage", "100%", "poll");
                    
                    parent.hubiForm_container(this, container, 1); 
                }
                parent.hubiForm_section(this, "Local Graph URL", 1, "link"){
                    container = [];
                    container << parent.hubiForm_text(this, "${state.localEndpointURL}graph/?access_token=${state.endpointSecret}");
                    
                    parent.hubiForm_container(this, container, 1); 
                }
                
                if (graph_update_rate){
                     parent.hubiForm_section(this, "Preview", 10, "show_chart"){                         
                         container = [];
                         container << parent.hubiForm_graph_preview(this)
                         
                         parent.hubiForm_container(this, container, 1); 
                     } //graph_timespan
            
                    parent.hubiForm_section(this, "Hubigraph Tile Installation", 2, "apps"){
                        container = [];
                             
                        container << parent.hubiForm_switch(this, title: "Install Hubigraph Tile Device?", name: "install_device", default: false, submit_on_change: true);
                        if (install_device==true){ 
                             container << parent.hubiForm_text_input(this, "Name for HubiGraph Tile Device", "device_name", "Hubigraph Tile", "false");
                        }
                        parent.hubiForm_container(this, container, 1); 
                    }
                } 
             
            
               if (state.endpoint){
                   parent.hubiForm_section(this, "Hubigraph Application", 1, "settings"){
                        container = [];
                        container << parent.hubiForm_sub_section(this, "Application Name");
                        container << parent.hubiForm_text_input(this, "Rename the Application?", "app_name", "Hubigraph Bar Graph", "false");
                        container << parent.hubiForm_sub_section(this, "Debugging");
                        container << parent.hubiForm_switch(this, title: "Enable Debug Logging?", name: "debug", default: false);
                        container << parent.hubiForm_sub_section(this, "Disable Oauth Authorization");
                        container << parent.hubiForm_page_button(this, "Disable API", "disableAPIPage", "100%", "cancel");  
                       
                        parent.hubiForm_container(this, container, 1); 
                    }
               }
       
            } //else 
        
    } //dynamicPage
}

def installed() {
    log.debug "Installed with settings: ${settings}"
    updated();
}

def uninstalled() {
    if (state.endpoint) {
        try {
            log.debug "Revoking API access token"
            revokeAccessToken()
        }
        catch (e) {
            log.warn "Unable to revoke API access token: $e"
        }
    }
    removeChildDevices(getChildDevices());
}

private removeChildDevices(delete) {
	delete.each {deleteChildDevice(it.deviceNetworkId)}
}


def updated() {
    app.updateLabel(app_name);
    state.dataName = attribute;
    
     if (install_device == true){
        parent.hubiTool_create_tile(this);
    }
}

def buildData() {
    def resp = [:]
    def now = new Date();
    def then = new Date(0);
            
    if(sensors) {
      sensors.each {sensor ->
          def attributes = settings["attributes_${sensor.id}"];
          resp[sensor.id] = [:];
          attributes.each { attribute ->
              if (attribute == "lastupdate"){
                    lastEvent = sensor.getLastActivity();
                    latest = lastEvent ? Date.parse("yyyy-MM-dd'T'hh:mm:ssZ", sensor.getLastActivity().toString()).getTime() : 0;
                    resp[sensor.id][attribute] = [current: (now.getTime()-latest), date: latest];
              } else {
                    latest = sensor.latestState(attribute);
                    resp[sensor.id][attribute] = [current: latest.getValue(), date: latest.getDate()];
              }
          }
      }
   }
   return resp
}

def getChartOptions(){
    
    colors = [];
    sensors.each {sensor->
        def attributes = settings["attributes_${sensor.id}"];
        attributes.each {attribute->
            attrib_string = "attribute_${sensor.id}_${attribute}_color"
            transparent_attrib_string = "attribute_${sensor.id}_${attribute}_color_transparent"
            colors << (settings[transparent_attrib_string] ? "transparent" : settings[attrib_string]);
           
        }
    }
    
    if (graph_type == "1"){
        axis1 = "hAxis";
        axis2 = "vAxis";
    } else {
        axis1 = "vAxis";
        axis2 = "hAxis";
    }
    
    def options = [
        "graphUpdateRate": Integer.parseInt(graph_update_rate),
        "graphType": graph_type,
        "graphOptions": [
            "bar" : [ "groupWidth" : "100%",
                    ],
            "width": graph_static_size ? graph_h_size : "100%",
            "height": graph_static_size ? graph_v_size: "100%",
            "timeline": [
                "rowLabelStyle": ["fontSize": graph_axis_font, "color": graph_axis_color_transparent ? "transparent" : graph_axis_color],
                "barLabelStyle": ["fontSize": graph_axis_font]
            ],
            "backgroundColor": graph_background_color_transparent ? "transparent" : graph_background_color,
            "isStacked": true,
            "chartArea": [ "left": 10,
                           "right" : 10, 
                           "top": 10, 
                           "bottom": 10 ],
            "legend" : [ "position" : "none" ],
            "hAxis": [ "textPosition": "none", 
                       "gridlines" : [ "count" : "0" ]
                     ],
         
            "vAxis": [ "textPosition": "none",
                       "gridlines" : [ "count" : "0" ]
                     ],
            "annotations" : [    "alwaysOutside": "false",
                                 "textStyle": [
      					            "fontSize": annotation_font,
      					            "bold":     annotation_bold,
      					            "italic":   annotation_italic,
      	         					"color":    annotation_color_transparent ? "transparent" : annotation_color,
      					            "auraColor":annotation_aura_color_transparent ? "transparent" : annotation_aura_color,
				                 ],
                                 "stem": [ "color": "transparent",
                                          ],
                                 "highContrast": "false"
                             ],		 
         ],
    ]
    return options;
}
        
void removeLastChar(str) {
    str.subSequence(0, str.length() - 1)
}

def getTimeLine() {
    def fullSizeStyle = "margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden";
    
    def html = """
    <html style="${fullSizeStyle}">
        <head>
            <script src="https://code.jquery.com/jquery-3.5.0.min.js" integrity="sha256-xNzN2a4ltkB44Mc/Jz3pT4iU1cmeR0FkXs4pru/JxaQ=" crossorigin="anonymous"></script>
            <script src="https://cdnjs.cloudflare.com/ajax/libs/moment.js/2.25.0/moment.min.js" integrity="sha256-imB/oMaNA0YvIkDkF5mINRWpuFPEGVCEkHy6rm2lAzA=" crossorigin="anonymous"></script>
            <script src="https://cdnjs.cloudflare.com/ajax/libs/he/1.2.0/he.min.js" integrity="sha256-awnFgdmMV/qmPoT6Fya+g8h/E4m0Z+UFwEHZck/HRcc=" crossorigin="anonymous"></script>
            <script type="text/javascript" src="https://www.gstatic.com/charts/loader.js"></script>
            <script src="/local/a930f16d-d5f4-4f37-b874-6b0dcfd47ace-HubiGraph.js"></script>
            <script type="text/javascript">
google.charts.load('current', {'packages':['corechart']});

let options = [];
let subscriptions = {};
let graphData = {};

//stack for accumulating points to average
let stack = {};

let websocket;

class Loader {
    constructor() {
        this.elem = jQuery(jQuery(document.body).prepend(`
            <div class="loaderContainer">
                <div class="dotsContainer">
                    <div class="dot"></div>
                    <div class="dot"></div>
                    <div class="dot"></div>
                </div>
                <div class="text"></div>
            </div>
        `).children()[0]);
    }

    setText(text) {
        this.elem.find('.text').text(text);
    }

    remove() {
        this.elem.remove();
    }
}

function getOptions() {
    return jQuery.get("${state.localEndpointURL}getOptions/?access_token=${state.endpointSecret}", (data) => {
        options = data;
        console.log("Got Options");
        console.log(options);
    });
}

function getSubscriptions() {
    return jQuery.get("${state.localEndpointURL}getSubscriptions/?access_token=${state.endpointSecret}", (data) => {
        console.log("Got Subscriptions");
        subscriptions = data;

    });
}

function getValue(data, date, attr){

    
    if (options.graphType == "time" || attr == "lastupdate") {
        let now = new Date();
        let then = new Date(date);        
        return now.getTime()-then.getTime();
    }

    switch (data) {
       case "active"       : return 100;
       case "inactive"     : return 0;
       case "on"           : return 100;
       case "off"          : return 0;
       case "open"         : return 100;
       case "closed"       : return 0;
       case "detected"     : return 100;
       case "not detected" : return 0;
       case "clear"        : return 0;
       case "wet"          : return 100;
       case "dry"          : return 0;
       
       
        
    }
    return data;
}



function getGraphData() {
    return jQuery.get("${state.localEndpointURL}getData/?access_token=${state.endpointSecret}", (data) => {
        graphData = data;
    });
}

function parseEvent(event) {
    let deviceId = event.deviceId;

    //only accept relevent events
    if((subscriptions.ids.includes(deviceId) && subscriptions.attributes[deviceId].includes(event.name)) ||
       (subscriptions.ids.includes(deviceId) && subscriptions.attributes[deviceId].includes("lastupdate"))) {
        let value = event.value;
        let attribute = event.name;
        
        console.log("Trigger: ", attribute, "Value: ", value);

        if (subscriptions.attributes[deviceId].includes("lastupdate")){
            let now = new Date();
            graphData[deviceId]["lastupdate"].current = now.getTime();
            graphData[deviceId]["lastupdate"].date = new Date();
        } else { 
            graphData[deviceId][attribute].current = value;
            graphData[deviceId][attribute].date = new Date();
        }
        
        
        //update if we are realtime
        if(options.graphUpdateRate === 0) update();
    }
}

function update(callback) {
    drawChart(callback);
}

async function onLoad() {
    //append our css
    jQuery(document.head).append(`
        <style>
            .loaderContainer {
                position: fixed;
                z-index: 100;

                width: 100%;
                height: 100%;

                background-color: white;

                display: flex;
                flex-flow: column nowrap;
                justify-content: center;
                align-items: middle;
            }

            .dotsContainer {
                height: 60px;
                padding-bottom: 10px;

                display: flex;
                flex-flow: row nowrap;
                justify-content: center;
                align-items: flex-end;
            }

            @keyframes bounce {
                0% {
                    transform: translateY(0);
                }

                50% {
                    transform: translateY(-50px);
                }

                100% {
                    transform: translateY(0);
                }
            }

            .dot {
                box-sizing: border-box;

                margin: 0 25px;

                width: 10px;
                height: 10px;

                border: solid 5px black;
                border-radius: 5px;

                animation-name: bounce;
                animation-duration: 1s;
                animation-iteration-count: infinite;
            }

            .dot:nth-child(1) {
                animation-delay: 0ms;
            }

            .dot:nth-child(2) {
                animation-delay: 333ms;
            }

            .dot:nth-child(3) {
                animation-delay: 666ms;
            }

            .text {
                font-family: Arial;
                font-weight: 200;
                font-size: 2rem;
                text-align: center;
            }
        </style>
    `);

    let loader = new Loader();

    //first load
    loader.setText('Getting options (1/4)');
    await getOptions();
    loader.setText('Getting device data (2/4)');
    await getSubscriptions();
    loader.setText('Getting events (3/4)');
    await getGraphData();
    loader.setText('Drawing chart (4/4)');

    update(() => {
        //destroy loader when we are done with it
        loader.remove();
    });

    //start our update cycle
    if(options.graphUpdateRate !== -1) {
        //start websocket
        websocket = new WebSocket("ws://" + location.hostname + "/eventsocket");
        websocket.onopen = () => {
            console.log("WebSocket Opened!");
        }
        websocket.onmessage = (event) => {
            parseEvent(JSON.parse(event.data));
        }

        if(options.graphUpdateRate !== 0) {
            setInterval(() => {
                update();
            }, options.graphUpdateRate);
        }
    }

    //attach resize listener
    window.addEventListener("resize", () => {
        drawChart();
    });
}

function onBeforeUnload() {
    if(websocket) websocket.close();
}

function dd(num){
    if (num<10) return "0"+num.toString();
    else return num.toString();
}


function convertToString(msec){
    
    if (msec == "0" || msec == 0) return "0 Seconds ago";
    
    let days =  parseInt(Math.floor(msec/86400000));
    let hours = parseInt(Math.floor((msec%86400000)/3600000));
    let mins =  parseInt(Math.floor((msec%3600000)/60000));
    let secs =  parseInt(Math.floor((msec%60000)/1000));
    
    let dayString = days == 0 ? "" : days.toString()+" Days";
        dayString = days == 1 ? "1 Day" : dayString
    let hourString = hours == 0 ? "" : hours.toString()+" Hours ";
        hourString = hours == 1 ?  "1 Hour" : hourString;
    let minuteString = mins == 0 ? "" : mins.toString()+" Minutes ";
        minuteString = mins == 1 ?  "1 Minute" : minuteString;
    let secondString = secs == 0 ? "" : secs.toString()+" Seconds ";
        secondString = secs == 1 ?  "1 Second" : secondString;
    
    
    return dayString+" "+hourString+" "+minuteString+" "+secondString;
}


function getDataList(){
    const date_options = {
         weekday: "long",
         year: "numeric",
         month:"long",
         day:"numeric"
    };
    const time_options ={
         hour12 : true,
         hour:  "2-digit",
         minute: "2-digit",
         second: "2-digit"
    };    

    let data = [];

    subscriptions.order.forEach(orderStr => {
          const splitStr = orderStr.split('_');
          const deviceId = splitStr[1];
          const attr = splitStr[2];
          const event = graphData[deviceId][attr];
        
          const cur_ = parseFloat(getValue(event.current, event.date, attr));
          var cur_String = '';
          var units_ = ``;
        
          var t_date = new Date(event.date);
          var date_String = t_date.toLocaleDateString("en-US",date_options);
          var time_String = t_date.toLocaleTimeString("en-US",time_options);

          const name = subscriptions.labels[deviceId][attr].replace('%deviceName%', subscriptions.sensors[deviceId].displayName).replace('%attributeName%', attr);
        
          var value_ = event.current;
          var stats_ = `\${name}\nCurrent: \${value_}\${units_}\nDate: \${date_String} \${time_String}`;

          if (attr == "lastupdate"){
                value_ = convertToString(value_);
                stats_ = `\${name} \nLast Update: \${value_}\${units_}\nDate: \${date_String} \${time_String}`;
          }
          
        
          data.push({name: name, value: cur_, str: stats_});
      });

    return data;
}

function drawChart(callback) {

    //get number of elements
    
    let numElements = subscriptions.count;

    let colorProfile = [];
    for (i=0; i<subscriptions.num_gradients; i++)
        colorProfile.push(subscriptions.gradients[i]);
    
                         
    let dataArray = [];
    let tempArray = [];
    let dim = getRowColumnsBlank(numElements);
    let map = new Map();
    let cols = subscriptions.num_columns;
    let rows = Math.ceil(numElements/cols);

      
    //Build the header based on the number of elements  
    let header = [];
    header.push('Device');
    for (i=0; i< cols; i++){
      	header.push("R"+i);
        header.push({role:"style"});
        header.push({role:"tooltip"});
        header.push({role:"annotation"});
    }
    
    dataArray.push(header);
    
    let data = getDataList();
    

    let idx = 0;
    let color = 0;
    let width = subscriptions.line_thickness;
    let line_color = subscriptions.line_color;
    let fill_opacity = 1.0;
    for (i=0; i<rows; i++){
      	tempArray = [];
      	tempArray.push("Row"+i);
        for (j=0; j<cols; j++){
            
            if (idx>= numElements) {
                tempArray.push(0);
                value = ''
                str = '';
                color = options.graphOptions.backgroundColor;
                line_color = subscriptions.line_color;
                opacity = 0.0;
                width = 0;
                fill_opacity = 0.0;
                attr = '';
            } else {
                tempArray.push(10);
                value = data[idx].value;
                str = data[idx].str;
                color = getcolor(colorProfile, value);
                line_color = subscriptions.line_color;
                opacity = 1.0;
                width = subscriptions.line_thickness;
                if (subscriptions.show_annotations){
                    val = parseFloat(value).toFixed(subscriptions.decimals);
                    attr = val;
                } else {
                    attr = '';
                }
            }
            
        	tempArray.push('stroke-color: '+line_color+'; stroke-opacity: '+opacity+'; stroke-width: '+width+'; color: '+color+'; fill-opacity: '+fill_opacity );
            tempArray.push(str);
            tempArray.push(attr);     
            idx++;
        }
        dataArray.push(tempArray);
    }
    var dataTable = google.visualization.arrayToDataTable(dataArray);
    
    chart = new google.visualization.BarChart(document.getElementById("timeline"));
   
    if(callback) google.visualization.events.addListener(chart, 'ready', callback);

    chart.draw(dataTable, options.graphOptions);
}

google.charts.setOnLoadCallback(onLoad);
window.onBeforeUnload = onBeforeUnload;
        </script>
      </head>
      <body style="${fullSizeStyle}">
          <div id="timeline" style="${fullSizeStyle}" align="center"></div>
      </body>
    </html>
    """
    
return html;
}

// Create a formatted date object string for Google Charts Timeline
def getDateString(date) {
    def dateObj = Date.parse("yyyy-MM-dd HH:mm:ss.SSS", date.toString())
    //def dateObj = date
    def year = dateObj.getYear() + 1900
    def dateString = "new Date(${year}, ${dateObj.getMonth()}, ${dateObj.getDate()}, ${dateObj.getHours()}, ${dateObj.getMinutes()}, ${dateObj.getSeconds()})"
    dateString
}

// Events come in Date format
def getDateStringEvent(date) {
    def dateObj = date
    def yyyy = dateObj.getYear() + 1900
    def MM = String.format("%02d", dateObj.getMonth()+1);
    def dd = String.format("%02d", dateObj.getDate());
    def HH = String.format("%02d", dateObj.getHours());
    def mm = String.format("%02d", dateObj.getMinutes());
    def ss = String.format("%02d", dateObj.getSeconds());
    def dateString = /$yyyy-$MM-$dd $HH:$mm:$ss.000/;
    dateString
}
    
def initializeAppEndpoint() {
    if (!state.endpoint) {
        try {
            def accessToken = createAccessToken()
            if (accessToken) {
                state.endpoint = getApiServerUrl()
                state.localEndpointURL = fullLocalApiServerUrl("")  
                state.remoteEndpointURL = fullApiServerUrl("")
                state.endpointSecret = accessToken
            }
        }
        catch(e) {
            log.debug("Error: $e");
            state.endpoint = null
        }
    }
    return state.endpoint
}

def getColorCode(code){
    
    ret = "#FFFFFF"
    switch (code){
        case 7:  ret = "#800000"; break;
        case 1:	 ret = "#FF0000"; break;
        case 6:	ret = "#FFA500"; break;	
        case 8:	ret = "#FFFF00"; break;	
        case 9:	ret = "#808000"; break;	
        case 2:	ret = "#008000"; break;	
        case 5:	ret = "#800080"; break;	
        case 4:	ret = "#FF00FF"; break;	
        case 10: ret = "#00FF00"; break;	
        case 11: ret = "#008080"; break;	
        case 12: ret = "#00FFFF"; break;	
        case 3:	ret = "#0000FF"; break;	
        case 13: ret = "#000080"; break;	
    }
    return ret;
}

//oauth endpoints
def getGraph() {
    return render(contentType: "text/html", data: getTimeLine());      
}

def getData() {
    def data = buildData();
         
    return render(contentType: "text/json", data: JsonOutput.toJson(data));
}

def getOptions() {
    return render(contentType: "text/json", data: JsonOutput.toJson(getChartOptions()));
}

def getSubscriptions() {
    def count_ = 0;
    def _ids = [];
    def _attributes = [:];
    def labels = [:];
    def gradients = [:];
    sensors.each { sensor ->
        _ids << sensor.idAsLong;
        _attributes[sensor.id] = [];
        labels[sensor.id] = [:];
        def attributes = settings["attributes_${sensor.id}"];
        attributes.each { attribute ->
            count_ ++;
            _attributes[sensor.id] << attribute;
            labels[sensor.id][attribute] = "${sensor.id} ${attribute}";
            
        }
        labels[sensor.id] = [:];
            settings["attributes_${sensor.id}"].each { attr ->
                labels[sensor.id][attr] = settings["graph_name_override_${sensor.id}_${attr}"];
        }
                                               
    }
                                                                                    
    def sensors_fmt = [:];
    sensors.each { it ->
        sensors_fmt[it.id] = [ "id": it.id, "displayName": it.displayName, "currentStates": it.currentStates ];
    }
    for (i=0; i<graph_num_gradients; i++){
        gradients[i] = ["val": settings["graph_gradient_${i}_value"], "color": settings["graph_gradient_${i}_color"]];
    }
   
    
    def order = parseJson(graph_order);
   
    def subscriptions = [
        "decimals" : graph_decimals,
        "count" : count_,
        "sensors": sensors_fmt,
        "ids": _ids,
        "attributes": _attributes,
        "labels": labels,
        "order": order,
        "show_annotations": show_annotations,
        "gradients": gradients,
        "num_gradients" : graph_num_gradients,
        "num_columns" : graph_num_columns,
        "line_color" : graph_line_color,
        "line_thickness" : graph_line_size,
    ];
    
    return render(contentType: "text/json", data: JsonOutput.toJson(subscriptions));
}
