declare class RFIDScanner {
  removeAllListeners?(): void;
  setOnTagEvent?(callback: Function): void;
  setOnRfidStatusEvent?(callback: Function): void;
  setOnBarcodeTriggerEvent?(callback: Function): void;
  setOnWriteTagEvent?(callback: Function): void;
  setOnTriggerActionEvent?(callback: Function): void;
  setOnLocateTagEvent?(callback: Function): void;
  initialThread?(): void;
  connect?(): Promise<boolean>;
  disconnect?(): void;
  attemptToReconnect?(): Promise<boolean>;
  isConnected?(): Promise<boolean>;
  cleanTags?(): void;
  getDeviceList?(): Promise<Array<{ name: string; address: string }>>;
  saveCurrentRoute?(value: string): Promise<boolean>;
  saveSelectedScanner?(value: string): void;
  getConnectedReader?(): Promise<string>;
  setAntennaLevel?(value: number): void;
  readBarcode?(value: boolean): Promise<boolean>;
  programTag?(oldTag: string, newTag: string): Promise<boolean>;
  saveTagID?(tagId: string): Promise<boolean>;
}

export default RFIDScanner;

export enum RFIDScannerEvent {
  TAG = "TagEvent",
  TAGS = "tags",
  HANDLE_ERROR = "HandleError",
  WRITE_TAG = "writeTag",
  BARCODE = "barcode",
  BARCODE_TRIGGER = "BarcodeTrigger",
  LOCATE_TAG = "locateTag",
  TRIGGER_ACTION = "triggerAction",
  RFID_STATUS = "RFIDStatusEvent",
  BARCODE_ERROR = "barcodeError"
}
