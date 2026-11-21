/*
 * PharmaPrinter.ino
 * ESP32-S3 Cloud Thermal Printer Client using WiFiManager and Firebase RTDB.
 * Supporting dynamic Pharmacy Owner Username and Security Key changes at any time!
 * 
 * Hardware:
 * - ESP32-S3 DevKit (with Native USB OTG Port)
 * - USB-C OTG Adapter
 * - USB POS Thermal/Laser Printer (ESC/POS compatible)
 * 
 * Required Libraries (Install via Arduino Library Manager):
 * 1. WiFiManager by tzapu
 * 2. ArduinoJson by Benoit Blanchon (v6 or v7)
 */

#include <WiFi.h>
#include <WiFiManager.h>
#include <Preferences.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <ArduinoJson.h>

// ESP32-S3 Native USB Host Libraries for USB Printer communication
#include "usb/usb_host.h"
#include "esp_log.h"

// Preferences for non-volatile storage (NVS)
Preferences preferences;

// Custom Config Parameters
char owner_username[40] = "admin";
char security_key[20] = "123456";
char firebase_url[120] = "https://pharmasity-3cc40-default-rtdb.asia-southeast1.firebasedatabase.app";

// Flag for saving data from WiFiManager
bool shouldSaveConfig = false;

// Callback notifying us of the need to save config
void saveConfigCallback() {
  Serial.println("Should save config");
  shouldSaveConfig = true;
}

// USB Host ESC/POS Printer handling variables
static usb_device_handle_t print_device_handle = NULL;
static uint8_t bulk_out_endpoint = 0;
#define CLIENT_NUM_EVENT_MSG   5

// Forward declaration of USB functions
void initUSBHost();
void printViaUSB(const String &text);
void printRawBytes(const uint8_t *data, size_t len);

void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println("\n--- PHARMA PRINTER BOOTING ---");

  // Load custom configurations from ESP32 flash memory (NVS)
  preferences.begin("pharma-print", false);
  if (preferences.isKey("owner")) {
    preferences.getString("owner", owner_username, sizeof(owner_username));
  }
  if (preferences.isKey("seckey")) {
    preferences.getString("seckey", security_key, sizeof(security_key));
  }
  if (preferences.isKey("fburl")) {
    preferences.getString("fburl", firebase_url, sizeof(firebase_url));
  }
  Serial.printf("Loaded Configs - Owner: %s, SecKey: %s, Firebase: %s\n", owner_username, security_key, firebase_url);

  // Set up WiFiManager with custom parameters for Portal
  WiFiManager wm;
  wm.setSaveConfigCallback(saveConfigCallback);

  // Custom text fields for WiFiManager portal webpage
  WiFiManagerParameter custom_owner("owner", "Pharmacy Owner Username", owner_username, 40);
  WiFiManagerParameter custom_seckey("seckey", "Printer Security Key", security_key, 20);
  WiFiManagerParameter custom_fburl("fburl", "Firebase RTDB URL", firebase_url, 120);

  wm.addParameter(&custom_owner);
  wm.addParameter(&custom_seckey);
  wm.addParameter(&custom_fburl);

  // AP Config
  // If BOOT button is held on startup, or first boot, start setup AP
  pinMode(0, INPUT_PULLUP); // BOOT button pin on ESP32-S3
  if (digitalRead(0) == LOW) {
    Serial.println("BOOT Button pressed! Forcing Configuration Portal...");
    wm.resetSettings(); // Clear WiFi credentials to force Portal
  }

  // Starts an Access Point named "PharmaPrinter-Setup"
  // Connect with phone to 192.168.4.1 if it doesn't open automatically
  if (!wm.autoConnect("PharmaPrinter-Setup")) {
    Serial.println("Failed to connect and hit timeout. Restarting ESP32...");
    delay(3000);
    ESP.restart();
  }

  // Save the custom parameters back to NVS if configured
  if (shouldSaveConfig) {
    strcpy(owner_username, custom_owner.getValue());
    strcpy(security_key, custom_seckey.getValue());
    strcpy(firebase_url, custom_fburl.getValue());

    preferences.putString("owner", owner_username);
    preferences.putString("seckey", security_key);
    preferences.putString("fburl", firebase_url);
    Serial.println("Saved new configurations to Non-Volatile Storage (NVS)!");
  }
  preferences.end();

  Serial.println("Successfully Connected to WiFi!");
  Serial.print("IP Address: ");
  Serial.println(WiFi.localIP());

  // Initialize USB Host to communicate with the USB POS Printer
  initUSBHost();
}

void loop() {
  // If WiFi is disconnected, let ESP try to reconnect automatically
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("WiFi connection lost. Reconnecting...");
    delay(5000);
    return;
  }

  // Check Firebase for pending print jobs of this Owner/Admin
  checkAndProcessPrintJobs();

  // Poll interval
  delay(4000); // Poll Firebase every 4 seconds
}

void checkAndProcessPrintJobs() {
  WiFiClientSecure client;
  client.setInsecure(); // Skip SSL certificate validation for ease of use
  HTTPClient http;

  // Build Firebase endpoint URL for this specific owner/admin's print jobs
  String url = String(firebase_url) + "/print_jobs/" + String(owner_username) + ".json";
  
  if (http.begin(client, url)) {
    int httpCode = http.GET();
    if (httpCode == HTTP_CODE_OK) {
      String payload = http.getString();
      
      // Parse print jobs JSON
      DynamicJsonDocument doc(8192);
      DeserializationError error = deserializeJson(doc, payload);
      
      if (!error && !doc.isNull()) {
        JsonObject jobs = doc.as<JsonObject>();
        for (JsonPair jobPair : jobs) {
          String jobId = jobPair.key().c_str();
          JsonObject job = jobPair.value().as<JsonObject>();
          
          String status = job["status"].as<String>();
          if (status == "pending") {
            Serial.println("New Pending Print Job Found! ID: " + jobId);
            
            // Execute Printing Process
            executeThermalPrint(job);
            
            // Mark job as printed in Firebase
            markJobAsPrinted(jobId);
            break; // Print one job per cycle to prevent buffer overflow
          }
        }
      }
    } else {
      Serial.printf("Firebase check failed, HTTP Code: %d\n", httpCode);
    }
    http.end();
  }
}

void executeThermalPrint(JsonObject job) {
  String billNumber = job["billNumber"].as<String>();
  long timestamp = job["timestamp"].as<long>();
  String soldBy = job["soldBy"].as<String>();
  double totalAmount = job["totalAmount"].as<double>();
  double discount = job["discount"].as<double>();
  double finalAmount = job["finalAmount"].as<double>();
  bool isReturned = job["isReturned"].as<bool>();
  double returnedAmount = job["returnedAmount"].as<double>();
  String returnedBy = job["returnedBy"].as<String>();

  // Formatting timestamp
  String timeStr = String(timestamp); // Simply displaying the raw stamp or placeholder

  // Thermal Printer Commands (ESC/POS)
  const uint8_t ESC_Init[] = {0x1B, 0x40};       // Initialize printer
  const uint8_t ESC_Align_Center[] = {0x1B, 0x61, 0x01}; // Center alignment
  const uint8_t ESC_Align_Left[] = {0x1B, 0x61, 0x00};   // Left alignment
  const uint8_t ESC_Align_Right[] = {0x1B, 0x61, 0x02};  // Right alignment
  const uint8_t ESC_Bold_On[] = {0x1B, 0x45, 0x01};     // Bold mode on
  const uint8_t ESC_Bold_Off[] = {0x1B, 0x45, 0x00};    // Bold mode off
  const uint8_t ESC_Double_Size[] = {0x1D, 0x21, 0x11}; // Double width/height
  const uint8_t ESC_Normal_Size[] = {0x1D, 0x21, 0x00}; // Normal size
  const uint8_t ESC_Feed_Cut[] = {0x1D, 0x56, 0x41, 0x03}; // Feed paper and cut

  // 1. Initialize
  printRawBytes(ESC_Init, sizeof(ESC_Init));

  // 2. Header
  printRawBytes(ESC_Align_Center, sizeof(ESC_Align_Center));
  printRawBytes(ESC_Double_Size, sizeof(ESC_Double_Size));
  printRawBytes(ESC_Bold_On, sizeof(ESC_Bold_On));
  printViaUSB("PHARMACY RECEIPT\n");
  printRawBytes(ESC_Normal_Size, sizeof(ESC_Normal_Size));
  printRawBytes(ESC_Bold_Off, sizeof(ESC_Bold_Off));
  
  if (isReturned) {
    printRawBytes(ESC_Bold_On, sizeof(ESC_Bold_On));
    printViaUSB("*** RETURN SLIP ***\n");
    printRawBytes(ESC_Bold_Off, sizeof(ESC_Bold_Off));
  }
  
  printViaUSB("--------------------------------\n");

  // 3. Metadata
  printRawBytes(ESC_Align_Left, sizeof(ESC_Align_Left));
  printViaUSB("Bill No  : " + billNumber + "\n");
  printViaUSB("Owner ID : " + String(owner_username) + "\n");
  printViaUSB("Operator : @" + soldBy + "\n");
  if (isReturned) {
    printViaUSB("Returned By: @" + returnedBy + "\n");
  }
  printViaUSB("--------------------------------\n");

  // 4. Items Table
  printRawBytes(ESC_Bold_On, sizeof(ESC_Bold_On));
  printViaUSB("Item Name       Qty       Total\n");
  printRawBytes(ESC_Bold_Off, sizeof(ESC_Bold_Off));
  printViaUSB("--------------------------------\n");

  JsonArray items = job["items"].as<JsonArray>();
  for (JsonObject item : items) {
    String name = item["name"].as<String>();
    String qty = item["qty"].as<String>();
    double total = item["total"].as<double>();

    // Truncate name for standard 32-column thermal layout
    if (name.length() > 14) {
      name = name.substring(0, 12) + "..";
    }
    
    // Align columns
    char lineBuffer[35];
    snprintf(lineBuffer, sizeof(lineBuffer), "%-14s %-9s %6.2f\n", name.c_str(), qty.c_str(), total);
    printViaUSB(lineBuffer);
  }
  printViaUSB("--------------------------------\n");

  // 5. Totals Block
  printRawBytes(ESC_Align_Right, sizeof(ESC_Align_Right));
  
  char totalLine[32];
  snprintf(totalLine, sizeof(totalLine), "Sub-Total: %8.2f\n", totalAmount);
  printViaUSB(totalLine);

  if (discount > 0) {
    char discLine[32];
    snprintf(discLine, sizeof(discLine), "Discount: %8.2f\n", discount);
    printViaUSB(discLine);
  }

  printRawBytes(ESC_Bold_On, sizeof(ESC_Bold_On));
  char finalLine[32];
  if (isReturned) {
    snprintf(finalLine, sizeof(finalLine), "Refunded: %8.2f\n", returnedAmount);
  } else {
    snprintf(finalLine, sizeof(finalLine), "Net Paid: %8.2f\n", finalAmount);
  }
  printViaUSB(finalLine);
  printRawBytes(ESC_Bold_Off, sizeof(ESC_Bold_Off));

  // 6. Footer
  printRawBytes(ESC_Align_Center, sizeof(ESC_Align_Center));
  printViaUSB("\n");
  printViaUSB("Thank You!\n");
  printViaUSB("Software Powered by AI Studio\n");
  printViaUSB("\n\n");

  // 7. Cut paper
  printRawBytes(ESC_Feed_Cut, sizeof(ESC_Feed_Cut));
  Serial.println("Receipt printed successfully!");
}

void markJobAsPrinted(String jobId) {
  WiFiClientSecure client;
  client.setInsecure();
  HTTPClient http;

  // We PATCH the job's status to "printed" in Firebase
  String url = String(firebase_url) + "/print_jobs/" + String(owner_username) + "/" + jobId + ".json";
  
  if (http.begin(client, url)) {
    StaticJsonDocument<128> doc;
    doc["status"] = "printed";
    doc["printedAt"] = millis(); // ESP32 uptime
    
    String jsonStr;
    serializeJson(doc, jsonStr);
    
    int httpCode = http.PATCH(jsonStr);
    if (httpCode == HTTP_CODE_OK) {
      Serial.println("Job status updated to 'printed' successfully in Firebase!");
    } else {
      Serial.printf("Failed to update status, HTTP: %d\n", httpCode);
    }
    http.end();
  }
}

// -------------------------------------------------------------
// ESP32-S3 Native USB Host (OTG Port) driver for USB Thermal POS
// -------------------------------------------------------------
static void usb_lib_task(void *arg) {
  while (true) {
    usb_host_lib_handle_events(portMAX_DELAY);
  }
}

static void usb_device_cb(const usb_host_client_event_msg_t *event_msg, void *arg) {
  if (event_msg->event == USB_HOST_CLIENT_EVENT_NEW_DEV) {
    Serial.printf("USB Device Attached: Address %d\n", event_msg->new_dev.address);
    // Auto open USB device
    usb_host_device_open(NULL, event_msg->new_dev.address, &print_device_handle);
    // Configure default bulk endpoint (usually Endpoint 1 or 2 Bulk OUT for POS printers)
    bulk_out_endpoint = 0x01; // Standard printer endpoint
    Serial.println("USB POS Thermal Printer Initialized and Connected!");
  } else if (event_msg->event == USB_HOST_CLIENT_EVENT_DEV_GONE) {
    Serial.println("USB Device Detached!");
    if (print_device_handle) {
      usb_host_device_close(NULL, print_device_handle);
      print_device_handle = NULL;
    }
  }
}

void initUSBHost() {
  Serial.println("Initializing ESP32-S3 USB Host Mode...");
  
  usb_host_config_t host_config = {
    .intr_flags = ESP_INTR_FLAG_LEVEL1,
  };
  
  if (usb_host_install(&host_config) == ESP_OK) {
    xTaskCreatePinnedToCore(usb_lib_task, "usb_task", 4096, NULL, 2, NULL, 0);
    
    usb_host_client_config_t client_config = {
      .is_async = false,
      .max_num_event_msg = CLIENT_NUM_EVENT_MSG,
      .client_event_cb = usb_device_cb,
      .client_event_cb_arg = NULL
    };
    
    usb_host_client_register(&client_config, NULL);
    Serial.println("USB Host driver registered. Plug in your USB POS Printer to the OTG Port.");
  } else {
    Serial.println("Failed to install USB Host driver.");
  }
}

void printRawBytes(const uint8_t *data, size_t len) {
  if (print_device_handle != NULL && bulk_out_endpoint != 0) {
    // Send raw ESC/POS bytes over USB Host bulk transfer
    usb_transfer_t *transfer;
    usb_host_transfer_alloc(len, 0, &transfer);
    
    if (transfer) {
      memcpy(transfer->data_buffer, data, len);
      transfer->num_bytes = len;
      transfer->device_handle = print_device_handle;
      transfer->bEndpointAddress = bulk_out_endpoint;
      
      // Perform bulk transfer
      usb_host_transfer_submit(transfer);
      // Wait or free immediately for simple transmission
      delay(10);
      usb_host_transfer_free(transfer);
    }
  } else {
    // Fallback: Also mirror printed receipt output to Serial Monitor for testing
    for (size_t i = 0; i < len; i++) {
      if (data[i] >= 32 && data[i] < 127) {
        Serial.print((char)data[i]);
      } else if (data[i] == '\n') {
        Serial.println();
      }
    }
  }
}

void printViaUSB(const String &text) {
  printRawBytes((const uint8_t*)text.c_str(), text.length());
}
