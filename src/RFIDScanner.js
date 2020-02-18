import {
  NativeModules,
  DeviceEventEmitter,
  NativeEventEmitter
} from "react-native";
import _ from "lodash";
import { RFIDScannerEvent } from "./RFIDScannerEvent";

const { RNRfidTsl } = NativeModules;

let tagEvent = null;
let rfidStatusEvent = null;
let writeTagEvent = null;
let barcodeTriggerEvent = null;
let triggerActionEvent = null;
let locateTagEvent = null;
class RFIDScanner {
  removeAllListeners = () => {
    if (!_.isEmpty(this.tagEvent)) {
      this.tagEvent.remove();
    }
    if (!_.isEmpty(this.rfidStatusEvent)) {
      this.rfidStatusEvent.remove();
    }
    if (!_.isEmpty(barcodeTriggerEvent)) {
      this.barcodeTriggerEvent.remove();
    }
    if (!_.isEmpty(this.writeTagEvent)) {
      this.writeTagEvent.remove();
    }
    if (!_.isEmpty(this.triggerActionEvent)) {
      this.triggerActionEvent.remove();
    }
    if (!_.isEmpty(this.locateTagEvent)) {
      this.locateTagEvent.remove();
    }
  };

  setOnTagEvent = callback => {
    this.tagEvent = DeviceEventEmitter.addListener(
      RFIDScannerEvent.TAG,
      callback
    );
  };

  setOnRfidStatusEvent = callback => {
    this.rfidStatusEvent = DeviceEventEmitter.addListener(
      RFIDScannerEvent.RFID_STATUS,
      callback
    );
  };

  setOnBarcodeTriggerEvent = callback => {
    this.barcodeTriggerEvent = DeviceEventEmitter.addListener(
      RFIDScannerEvent.BARCODE_TRIGGER,
      callback
    );
  };

  setOnWriteTagEvent = callback => {
    this.writeTagEvent = DeviceEventEmitter.addListener(
      RFIDScannerEvent.WRITE_TAG,
      callback
    );
  };

  setOnTriggerActionEvent = callback => {
    this.triggerActionEvent = DeviceEventEmitter.addListener(
      RFIDScannerEvent.TRIGGER_ACTION,
      callback
    );
  };

  setOnLocateTagEvent = callback => {
    this.locateTagEvent = DeviceEventEmitter.addListener(
      RFIDScannerEvent.LOCATE_TAG,
      callback
    );
  };

  initialThread = () => {
    RNRfidTsl.InitialThread();
  };

  connect = () => {
    return RNRfidTsl.ConnectDevice();
  };

  disconnect = () => {
    return RNRfidTsl.DisconnectDevice();
  };

  attemptToReconnect = () => {
    return RNRfidTsl.AttemptToReconnect();
  };

  isConnected = () => {
    return RNRfidTsl.IsConnected();
  };

  cleanTags = () => {
    return RNRfidTsl.CleanCacheTags();
  };

  getDeviceList = async () => {
    return RNRfidTsl.GetDeviceList();
  };

  saveCurrentRoute = value => {
    return RNRfidTsl.SaveCurrentRoute(value);
  };

  saveSelectedScanner = name => {
    return RNRfidTsl.SaveSelectedScanner(name);
  };

  getConnectedReader = () => {
    return RNRfidTsl.GetConnectedReader();
  };

  getBatteryLevel = () => {
    return RNRfidTsl.GetBatteryLevel();
  };

  setAntennaLevel = number => {
    if (!_.isEmpty(number) && !_.isEmpty(number.antennaLevel)) {
      let level = number.antennaLevel;
      if (!_.isNumber(level)) level = parseInt(level);
      return RNRfidTsl.SetAntennaLevel(level);
    }
  };

  readBarcode = value => {
    return RNRfidTsl.ReadBarcode(value);
  };

  programTag = (oldTag, newTag) => {
    return RNRfidTsl.ProgramTag(oldTag, newTag);
  };

  saveTagID = tag => {
    return RNRfidTsl.SaveTagID(tag);
  };
}

export default RFIDScanner;
