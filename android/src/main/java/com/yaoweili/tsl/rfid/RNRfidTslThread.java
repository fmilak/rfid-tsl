package com.yaoweili.tsl.rfid;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;

import android.content.Context;

import com.uk.tsl.rfid.asciiprotocol.AsciiCommander;
import com.uk.tsl.rfid.asciiprotocol.commands.AlertCommand;
import com.uk.tsl.rfid.asciiprotocol.commands.BatteryStatusCommand;
import com.uk.tsl.rfid.asciiprotocol.commands.FactoryDefaultsCommand;
import com.uk.tsl.rfid.asciiprotocol.commands.InventoryCommand;
import com.uk.tsl.rfid.asciiprotocol.commands.SwitchActionCommand;
import com.uk.tsl.rfid.asciiprotocol.commands.WriteTransponderCommand;
import com.uk.tsl.rfid.asciiprotocol.device.ConnectionState;
import com.uk.tsl.rfid.asciiprotocol.device.IAsciiTransport;
import com.uk.tsl.rfid.asciiprotocol.device.ObservableReaderList;
import com.uk.tsl.rfid.asciiprotocol.device.Reader;
import com.uk.tsl.rfid.asciiprotocol.device.ReaderManager;
import com.uk.tsl.rfid.asciiprotocol.device.TransportType;
import com.uk.tsl.rfid.asciiprotocol.enumerations.Databank;
import com.uk.tsl.rfid.asciiprotocol.enumerations.QuerySelect;
import com.uk.tsl.rfid.asciiprotocol.enumerations.QuerySession;
import com.uk.tsl.rfid.asciiprotocol.enumerations.QueryTarget;
import com.uk.tsl.rfid.asciiprotocol.enumerations.SelectAction;
import com.uk.tsl.rfid.asciiprotocol.enumerations.SelectTarget;
import com.uk.tsl.rfid.asciiprotocol.enumerations.SwitchState;
import com.uk.tsl.rfid.asciiprotocol.enumerations.TriState;
import com.uk.tsl.rfid.asciiprotocol.responders.ICommandResponseLifecycleDelegate;
import com.uk.tsl.rfid.asciiprotocol.responders.ISwitchStateReceivedDelegate;
import com.uk.tsl.rfid.asciiprotocol.responders.ITransponderReceivedDelegate;
import com.uk.tsl.rfid.asciiprotocol.responders.LoggerResponder;
import com.uk.tsl.rfid.asciiprotocol.responders.SwitchResponder;
import com.uk.tsl.rfid.asciiprotocol.responders.TransponderData;
import com.uk.tsl.utils.HexEncoding;
import com.uk.tsl.utils.Observable;

import java.util.ArrayList;

public abstract class RNRfidTslThread extends Thread {
	private ReactApplicationContext context;

	private static String currentRoute = null;
	// The Reader currently in use
	private static Reader mReader = null;
	// Available reader list
	private static ArrayList<Reader> mReaders = null;
	// User selected reader
	private static String selectedReader = null;

	//Indicate to read barcode
	private static boolean isReadBarcode = false;

	private static InventoryCommand mInventoryCommand = null;
	private static InventoryCommand mInventoryResponder = null;
	private static boolean mAnyTagSeen;
	private static ArrayList<String> cacheTags = null;

	//Save tag for locating tag
	private static String tagID = null;
	//Indicate is in Find IT mode or not.
	private static boolean locateMode = false;
	//Program tag
	private static final WriteTransponderCommand mWriteCommand =
			WriteTransponderCommand.synchronousCommand();

	public RNRfidTslThread(ReactApplicationContext context) {
		this.context = context;
		Init();
	}

	public abstract void dispatchEvent(String name, WritableMap data);

	public abstract void dispatchEvent(String name, String data);

	public abstract void dispatchEvent(String name, WritableArray data);

	public abstract void dispatchEvent(String name, boolean data);

	public void onHostResume() {
//		if (mReader != null && ReaderManager.sharedInstance() != null) {
//			// setEnabled(true);
//			// Remember if the pause/resume was caused by ReaderManager - this will be
//			// cleared when ReaderManager.onResume() is called
//			// boolean readerManagerDidCauseOnPause =
//			// ReaderManager.sharedInstance().didCauseOnPause();
//
//			// The ReaderManager needs to know about Activity lifecycle changes
//			ReaderManager.sharedInstance().onResume();
//
//			// The Activity may start with a reader already connected (perhaps by another
//			// App)
//			// Update the ReaderList which will add any unknown reader, firing events
//			// appropriately
//			ReaderManager.sharedInstance().updateList();
//
//			// Locate a Reader to use when necessary
//			AutoSelectReader(true);
//
//		}
	}

	public void onHostPause() {
//		if (mReader != null && ReaderManager.sharedInstance() != null) {
//			setEnabled(false);
//			// Disconnect from the reader to allow other Apps to use it
//			// unless pausing when USB device attached or using the DeviceListActivity to
//			// select a Reader
//			if (!ReaderManager.sharedInstance().didCauseOnPause() && mReader != null) {
//				mReader.disconnect();
//			}
//
//			ReaderManager.sharedInstance().onPause();
//		}
	}

	public void onHostDestroy() {
		DisconnectDevice();

		LocalBroadcastManager.getInstance(this.context).unregisterReceiver(mCommanderMessageReceiver);
	}

	private void Init() {
		try {
			if (getCommander() != null && mReader.isConnected()) {
				DisconnectDevice();
			}

			// Ensure the shared instance of AsciiCommander exists
			AsciiCommander.createSharedInstance(this.context);

			final AsciiCommander commander = getCommander();

			// Ensure that all existing responders are removed
			commander.clearResponders();

			//Logger
			commander.addResponder(new LoggerResponder());

			// Add responder to enable the synchronous commands
			commander.addSynchronousResponder();

			// Configure the ReaderManager when necessary
			ReaderManager.create(this.context);

			// Add observers for changes
			ReaderManager.sharedInstance().getReaderList().readerAddedEvent().addObserver(mAddedObserver);
			ReaderManager.sharedInstance().getReaderList().readerUpdatedEvent().addObserver(mUpdatedObserver);
			ReaderManager.sharedInstance().getReaderList().readerRemovedEvent().addObserver(mRemovedObserver);

			// Register to receive notifications from the AsciiCommander
			LocalBroadcastManager.getInstance(this.context).registerReceiver(mCommanderMessageReceiver,
					new IntentFilter(AsciiCommander.STATE_CHANGED_NOTIFICATION));
		} catch (Exception ex) {
			HandleError(ex.getMessage(), "Init");
		}
	}

	private void InitInventory() {

		// Initiate tags array for saving scanned tags, and prevent duplicate tags.
		cacheTags = new ArrayList<>();

		// This is the command that will be used to perform configuration changes and
		// inventories
		mInventoryCommand = new InventoryCommand();
		mInventoryCommand.setResetParameters(TriState.YES);
		// Configure the type of inventory
		mInventoryCommand.setIncludeTransponderRssi(TriState.YES);
		mInventoryCommand.setIncludeChecksum(TriState.YES);
		mInventoryCommand.setIncludePC(TriState.YES);
		mInventoryCommand.setIncludeDateTime(TriState.YES);

		// Use an InventoryCommand as a responder to capture all incoming inventory
		// responses
		mInventoryResponder = new InventoryCommand();
		// Also capture the responses that were not from App commands
		mInventoryResponder.setCaptureNonLibraryResponses(true);

		// Notify when each transponder is seen
		mInventoryResponder.setTransponderReceivedDelegate(mInventoryDelegate);

		mInventoryResponder.setResponseLifecycleDelegate(new ICommandResponseLifecycleDelegate() {

			@Override
			public void responseEnded() {
				if (!mAnyTagSeen && mInventoryCommand.getTakeNoAction() != TriState.YES) {
					// sendMessageNotification("No transponders seen");
				}
				mInventoryCommand.setTakeNoAction(TriState.NO);
			}

			@Override
			public void responseBegan() {
				mAnyTagSeen = false;
			}
		});
	}

	private void InitTrigger() {
		//Trigger Init
		SwitchResponder switchResponder = new SwitchResponder();
		switchResponder.setSwitchStateReceivedDelegate(mSwitchDelegate);
		getCommander().addResponder(switchResponder);

		// Configure the switch actions
		SwitchActionCommand saCommand = SwitchActionCommand.synchronousCommand();
		// Enable asynchronous switch state reporting
		saCommand.setAsynchronousReportingEnabled(TriState.YES);

		getCommander().executeCommand(saCommand);
	}

	private void InitProgramTag() {
		mWriteCommand.setResetParameters(TriState.YES);

		mWriteCommand.setSelectOffset(0x20);
		mWriteCommand.setBank(Databank.ELECTRONIC_PRODUCT_CODE);
		mWriteCommand.setOffset(2);

		mWriteCommand.setSelectAction(SelectAction.DEASSERT_SET_B_NOT_ASSERT_SET_A);
		mWriteCommand.setSelectTarget(SelectTarget.SESSION_2);

		mWriteCommand.setQuerySelect(QuerySelect.ALL);
		mWriteCommand.setQuerySession(QuerySession.SESSION_2);
		mWriteCommand.setQueryTarget(QueryTarget.TARGET_B);

		mWriteCommand.setTransponderReceivedDelegate(mProgramTagDelegate);
	}

	private void InitLocateTag() throws Exception {
		if (getCommander() != null && getCommander().isConnected() && mInventoryCommand != null && tagID != null) {

			InventoryCommand locateTagCommand = new InventoryCommand();
			locateTagCommand.setResetParameters(TriState.YES);
			locateTagCommand.setTakeNoAction(TriState.YES);
			locateTagCommand.setIncludeTransponderRssi(TriState.YES);
			locateTagCommand.setInventoryOnly(TriState.NO);

			locateTagCommand.setQueryTarget(QueryTarget.TARGET_B);
			locateTagCommand.setQuerySession(QuerySession.SESSION_0);
			locateTagCommand.setSelectAction(SelectAction.DEASSERT_SET_B_NOT_ASSERT_SET_A);
			locateTagCommand.setSelectTarget(SelectTarget.SESSION_0);
			locateTagCommand.setSelectBank(Databank.ELECTRONIC_PRODUCT_CODE);

			locateTagCommand.setSelectData(tagID);
			locateTagCommand.setSelectLength(tagID.length() * 4);
			locateTagCommand.setSelectOffset(0x20);

			getCommander().executeCommand(locateTagCommand);
			if (!locateTagCommand.isSuccessful()) {
				String errorMsg = String.format(
						"%s failed!\nError code: %s\n",
						locateTagCommand.getClass().getSimpleName(), locateTagCommand.getErrorCode());
				Log.e("LocateTag", errorMsg);
//				throw new Exception(errorMsg);
			}
		} else {
			throw new Exception("Initialize locate tag fail");
		}
	}

	public boolean SaveTagID(String tag) {
		if (getCommander() != null && getCommander().isConnected()) {
			tagID = tag;
			return true;
		}
		return false;
	}

	public boolean ProgramTag(String oldTag, String newTag) {
		if (getCommander() != null && getCommander().isConnected()) {
			if (oldTag != null && newTag != null) {
				byte[] data = null;
				data = HexEncoding.stringToBytes(newTag);
				mWriteCommand.setData(data);
				mWriteCommand.setLength(data.length / 2);
				mWriteCommand.setSelectData(oldTag);
				mWriteCommand.setSelectLength(oldTag.length() * 4);
				getCommander().executeCommand(mWriteCommand);

				if (!mWriteCommand.isSuccessful()) {
					String errorMsg = String.format(
							"%s failed!\nError code: %s\n",
							mWriteCommand.getClass().getSimpleName(), mWriteCommand.getErrorCode());
					dispatchEvent("writeTag", errorMsg);
					return false;
				}
				return true;
			}
		}
		return false;
	}

	//Trigger Handler
	private final ISwitchStateReceivedDelegate mSwitchDelegate = new ISwitchStateReceivedDelegate() {

		@Override
		public void switchStateReceived(SwitchState state) {
			// Use the alert command to indicate the type of asynchronous switch press
			// No vibration just vary the tone & duration
			if (currentRoute != null) {
				WritableMap map = Arguments.createMap();
				if (SwitchState.OFF.equals(state)) {
					//Trigger Release
					if (isReadBarcode && currentRoute.equalsIgnoreCase("tagit")) {
						dispatchEvent("BarcodeTrigger", false);
					} else if (currentRoute.equalsIgnoreCase("tagit") ||
							currentRoute.equalsIgnoreCase("lookup")) {
						cacheTags = new ArrayList<>();
					} else if (currentRoute.equalsIgnoreCase("locateTag")) {
						map.putString("RFIDStatusEvent", "inventoryStop");
						dispatchEvent("triggerAction", map);
					}
				} else {
					//Trigger Pull
					if (isReadBarcode && currentRoute.equalsIgnoreCase("tagit")) {
						dispatchEvent("BarcodeTrigger", true);
					} else if (currentRoute.equalsIgnoreCase("lookup") ||
							currentRoute.equalsIgnoreCase("locatetag")) {
						map.putString("RFIDStatusEvent", "inventoryStart");
						dispatchEvent("triggerAction", map);
					}
				}
			}
		}
	};

	//Inventory Delegate Handler
	private final ITransponderReceivedDelegate mInventoryDelegate =
			new ITransponderReceivedDelegate() {
				@Override
				public void transponderReceived(TransponderData transponder, boolean moreAvailable) {
					//Inventory received tags
					mAnyTagSeen = true;
					String EPC = transponder.getEpc();
					int rssi = transponder.getRssi();

					if (locateMode) {
						//RSSI range from -45 to -70.
						long distance;
						if (rssi >= -45) {
							distance = 100;
						} else if (rssi < -70) {
							distance = 0;
						} else {
							double num = (70 + rssi) * 100 / 35;
							distance = Math.round(num);
						}

						WritableMap map = Arguments.createMap();
						map.putInt("distance", (int) distance);
						dispatchEvent("locateTag", map);
					} else {
						if (currentRoute != null && currentRoute.equalsIgnoreCase("tagit")) {
							if (rssi > -50) {
								if (addTagToList(EPC) && cacheTags.size() == 1) {
									dispatchEvent("TagEvent", EPC);
								}
							}
						} else {
							if (addTagToList(EPC)) {
								dispatchEvent("TagEvent", EPC);
							}
						}
					}

				}
			};

	//Program tag Delegate Handler
	private final ITransponderReceivedDelegate mProgramTagDelegate =
			new ITransponderReceivedDelegate() {
				@Override
				public void transponderReceived(TransponderData transponderData, boolean b) {
					String eaMsg = transponderData.getAccessErrorCode() == null ? "" : transponderData.getAccessErrorCode().getDescription() + " (EA)";
					String ebMsg = transponderData.getBackscatterErrorCode() == null ? "" : transponderData.getBackscatterErrorCode().getDescription() + " (EB)";
					String errorMsg = eaMsg + ebMsg;
					if (errorMsg.length() > 0) {
						dispatchEvent("writeTag", errorMsg);
					} else {
						dispatchEvent("writeTag", "success");
					}
				}
				//
			};

	public void DisconnectDevice() {
		if (mReader != null && getCommander() != null) {
			cacheTags = new ArrayList<>();

			setEnabled(false);

			// Remove observers for changes
			ReaderManager.sharedInstance().getReaderList().readerAddedEvent().removeObserver(mAddedObserver);
			ReaderManager.sharedInstance().getReaderList().readerUpdatedEvent().removeObserver(mUpdatedObserver);
			ReaderManager.sharedInstance().getReaderList().readerRemovedEvent().removeObserver(mRemovedObserver);

			// Unregister to receive notifications from the AsciiCommander
			// LocalBroadcastManager.getInstance(this.context).unregisterReceiver
			// (mCommanderMessageReceiver);

			mReader.disconnect();
			mReader = null;
			currentRoute = null;
			// Available reader list
			mReaders = null;
			// User selected reader
			selectedReader = null;

			//Indicate to read barcode
			isReadBarcode = false;

			mInventoryCommand = null;
			mInventoryResponder = null;
			cacheTags = null;

			//Save tag for locating tag
			tagID = null;
			//Indicate is in Find IT mode or not.
			locateMode = false;
		}
	}

	public boolean ConnectDevice() {
		if (selectedReader != null && mReaders != null) {
			for (Reader reader : mReaders) {
				if (reader.getDisplayName().equals(selectedReader)) {
					mReader = reader;
					mReader.connect();
					getCommander().setReader(mReader);
					break;
				}
			}
			return true;
		}
		return false;
	}

	public boolean AttemptToReconnect() {
		if (selectedReader != null) {
			AutoSelectReader(true);
			return true;
		}
		return false;
	}

	public boolean IsConnected() {
		if (getCommander() != null) {
			return getCommander().isConnected();
		}
		return false;
	}

	public void CleanCacheTags() {
		if (getCommander() != null && getCommander().isConnected()) {
			cacheTags = new ArrayList<>();
		}
	}

	private void setEnabled(boolean state) {
		// Update the commander for state changes
		if (state) {
			// Listen for transponders
			getCommander().addResponder(mInventoryResponder);
			// Listen for barcodes
			// getCommander().addResponder(mBarcodeResponder);
		} else {
			// Stop listening for transponders
			getCommander().removeResponder(mInventoryResponder);
			// Stop listening for barcodes
			// getCommander().removeResponder(mBarcodeResponder);
		}

	}

//	private void testForAntenna() {
//		if (getCommander().isConnected()) {
//			InventoryCommand testCommand = InventoryCommand.synchronousCommand();
//			testCommand.setTakeNoAction(TriState.YES);
//			getCommander().executeCommand(testCommand);
//			if (!testCommand.isSuccessful()) {
//				Log.e("Error",
//						"ER:Error! Code: " + testCommand.getErrorCode() + " " + testCommand.getMessages().toString());
//			}
//		}
//	}

	public void SaveCurrentRoute(String value) throws Exception {
		currentRoute = value;
		if (currentRoute != null) {
			setEnabled(true);
			if (locateMode && !currentRoute.equalsIgnoreCase("locateTag")) {
				InitInventory();
				locateMode = false;
			} else if (currentRoute.equalsIgnoreCase("locateTag")) {
				InitLocateTag();
				locateMode = true;
			}

		} else {
			setEnabled(false);
		}
	}

	public WritableArray GetDeviceList() {
		WritableArray deviceList = Arguments.createArray();
		ReaderManager.sharedInstance().updateList();
		mReaders = ReaderManager.sharedInstance().getReaderList().list();
		for (Reader reader : mReaders) {
			WritableMap map = Arguments.createMap();
			map.putString("name", reader.getDisplayName());
			map.putString("address", reader.getDisplayInfoLine());
			deviceList.pushMap(map);
		}
		return deviceList;
	}

	private String GetBatteryLevel() {
		String level = null;
		if (getCommander() != null && getCommander().isConnected()) {
			BatteryStatusCommand bCommand = BatteryStatusCommand.synchronousCommand();
			getCommander().executeCommand(bCommand);
			int batteryLevel = bCommand.getBatteryLevel();
			Log.e("BatteryLevel", batteryLevel + "");
			level = String.valueOf(batteryLevel);
		}
		return level;
	}

	private void SetBuzzer(boolean value) {
		if (getCommander() != null && getCommander().isConnected()) {
			AlertCommand aCommand = AlertCommand.synchronousCommand();
			aCommand.setEnableBuzzer(value ? TriState.YES : TriState.NO);
			aCommand.setEnableVibrator(value ? TriState.YES : TriState.NO);
			getCommander().executeCommand(aCommand);
		}
	}
//
//	private int GetAntennaLevel() {
//		if (getCommander() != null && getCommander().isConnected()) {
//			getCommander().executeCommand(getInventoryCommand());
//			int level = getInventoryCommand().getOutputPower();
//			if (level > 0)
//				return level;
//		}
//		return 0;
//	}

	public void SetAntennaLevel(int level) {
		if (getCommander() != null && getCommander().isConnected()) {
			mInventoryCommand.setOutputPower(level);
			mInventoryCommand.setTakeNoAction(TriState.YES);
			getCommander().executeCommand(mInventoryCommand);
		}
	}

	private void updateConfiguration() {
		if (getCommander() != null && getCommander().isConnected()) {
			mInventoryCommand.setTakeNoAction(TriState.YES);
			getCommander().executeCommand(mInventoryCommand);
		}
	}

	public void SaveSelectedScanner(String name) {
		selectedReader = name;
	}

	public String GetConnectedReader() {
		return selectedReader;
	}

	//
	// Select the Reader to use and reconnect to it as needed
	//
	private void AutoSelectReader(boolean attemptReconnect) {
		ObservableReaderList readerList = ReaderManager.sharedInstance().getReaderList();
		Reader usbReader = null;
		if (readerList.list().size() >= 1) {
			// Currently only support a single USB connected device so we can safely take
			// the
			// first CONNECTED reader if there is one
			for (Reader reader : readerList.list()) {
				IAsciiTransport transport = reader.getActiveTransport();
				if (reader.hasTransportOfType(TransportType.USB)) {
					usbReader = reader;
					break;
				}
			}
		}

		if (mReader == null) {
			if (usbReader != null) {
				// Use the Reader found, if any
				mReader = usbReader;
				getCommander().setReader(mReader);
			}
		} else {
			// If already connected to a Reader by anything other than USB then
			// switch to the USB Reader
			IAsciiTransport activeTransport = mReader.getActiveTransport();
			if (activeTransport != null && activeTransport.type() != TransportType.USB && usbReader != null) {
				// appendMessage("Disconnecting from: " + mReader.getDisplayName());
				mReader.disconnect();

				mReader = usbReader;

				// Use the Reader found, if any
				getCommander().setReader(mReader);
			}
		}

		// Reconnect to the chosen Reader
		if (mReader != null && (mReader.getActiveTransport() == null
				|| mReader.getActiveTransport().connectionStatus().value() == ConnectionState.DISCONNECTED)) {
			// Attempt to reconnect on the last used transport unless the ReaderManager is
			// cause of OnPause (USB device connecting)
			if (attemptReconnect) {
				if (mReader.allowMultipleTransports() || mReader.getLastTransportType() == null) {
					// appendMessage("Connecting to: " + mReader.getDisplayName());
					// Reader allows multiple transports or has not yet been connected so connect to
					// it over any available transport
					mReader.connect();
				} else {
					// appendMessage("Connecting (over last transport) to: " +
					// mReader.getDisplayName());
					// Reader supports only a single active transport so connect to it over the
					// transport that was last in use
					mReader.connect(mReader.getLastTransportType());
				}
			}
		}
	}

	// ReaderList Observers
	private Observable.Observer<Reader> mAddedObserver = new Observable.Observer<Reader>() {
		@Override
		public void update(Observable<? extends Reader> observable, Reader reader) {
			// Log.e("mAddedObserver", "mAddedObserver");
			// See if this newly added Reader should be used
			// AutoSelectReader(true);
		}
	};

	private Observable.Observer<Reader> mUpdatedObserver = new Observable.Observer<Reader>() {
		@Override
		public void update(Observable<? extends Reader> observable, Reader reader) {
			// Log.e("mUpdatedObserver", "mUpdatedObserver");
		}
	};

	private Observable.Observer<Reader> mRemovedObserver = new Observable.Observer<Reader>() {
		@Override
		public void update(Observable<? extends Reader> observable, Reader reader) {
			// Log.e("mRemovedObserver", "mRemovedObserver");
			// Was the current Reader removed
			if (reader == mReader) {
				mReader = null;

				// Stop using the old Reader
				getCommander().setReader(mReader);
			}
		}
	};

	/**
	 * @return the current AsciiCommander
	 */
	private AsciiCommander getCommander() {
		return AsciiCommander.sharedInstance();
	}

	//
	// Handle the messages broadcast from the AsciiCommander
	//
	private BroadcastReceiver mCommanderMessageReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {

			try {
				// String connectionStateMsg = intent.getStringExtra(AsciiCommander.REASON_KEY);
				WritableMap map = Arguments.createMap();
				if (getCommander().getConnectionState().equals(ConnectionState.CONNECTED)) {
					resetDevice();
					InitInventory();
					InitTrigger();
					InitProgramTag();
					SetBuzzer(false);
					updateConfiguration();
					SetAntennaLevel(getCommander().getDeviceProperties().getMaximumCarrierPower());
					String battery = GetBatteryLevel();
					map.putBoolean("ConnectionState", true);
					map.putString("BatteryLevel", battery);
					dispatchEvent("RFIDStatusEvent", map);
				} else if (getCommander().getConnectionState().equals(ConnectionState.DISCONNECTED)) {
					map.putBoolean("ConnectionState", false);
					dispatchEvent("RFIDStatusEvent", map);
				}
			} catch (Exception ex) {
				HandleError(ex.getMessage(), "mCommanderMessageReceiver");
			}
		}
	};

	public void ReadBarcode(boolean value) {
		isReadBarcode = value;

		//If read barcode, then turn off RFID mode.
		setEnabled(!value);
	}

	private void resetDevice() {
		if (getCommander().isConnected()) {
			FactoryDefaultsCommand fdCommand = new FactoryDefaultsCommand();
			fdCommand.setResetParameters(TriState.YES);
			getCommander().executeCommand(fdCommand);
		}
	}

	private void HandleError(String msg, String code) {
		Log.e(code, msg);
//		String msg = ex.getMessage();
		WritableMap map = Arguments.createMap();
		map.putString("code", code);
		map.putString("msg", msg);
		dispatchEvent("HandleError", map);
	}

	private boolean addTagToList(String strEPC) {
		if (strEPC != null) {
			if (!checkIsExisted(strEPC)) {
				cacheTags.add(strEPC);
				return true;
			}
		}
		return false;
	}

	private boolean checkIsExisted(String strEPC) {
		for (int i = 0; i < cacheTags.size(); i++) {
			String tag = cacheTags.get(i);
			if (strEPC != null && strEPC.equals(tag)) {
				return true;
			}
		}
		return false;
	}
}
