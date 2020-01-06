package org.apache.cordova.posPlugin;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.lang.UnsupportedOperationException;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.dspread.xpos.EmvAppTag;
import com.dspread.xpos.EmvCapkTag;
import com.dspread.xpos.QPOSService;
import com.dspread.xpos.QPOSService.CardTradeMode;
import com.dspread.xpos.QPOSService.CommunicationMode;
import com.dspread.xpos.QPOSService.Display;
import com.dspread.xpos.QPOSService.DoTradeResult;
import com.dspread.xpos.QPOSService.EMVDataOperation;
import com.dspread.xpos.QPOSService.EmvOption;
import com.dspread.xpos.QPOSService.Error;
import com.dspread.xpos.QPOSService.QPOSServiceListener;
import com.dspread.xpos.QPOSService.TransactionResult;
import com.dspread.xpos.QPOSService.TransactionType;
import com.dspread.xpos.QPOSService.UpdateInformationResult;
import com.pnsol.sdk.miura.commands.Command;
import com.printer.CanvasPrint;
import com.printer.FontProperty;
import com.printer.PrinterConstants;
import com.printer.PrinterInstance;
import com.printer.PrinterType;
import com.printer.Table;
import com.printer.bluetooth.BluetoothPort;

import Decoder.BASE64Decoder;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.ContactsContract.AggregationExceptions;
import android.support.v4.app.ActivityCompat;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

/**
 * This class echoes a string called from JavaScript.
 */
public class dspread_pos_plugin extends CordovaPlugin {

	// Transaction properties.
	private final String currencyCodeMex = "484";
	private String amount;
	private String cashback;
	private TransactionType transactType;

	// SDK Instance.
	private QPOSService sdk;
	private MyPosListener sdkListener;
	private String terminalTime = new SimpleDateFormat("yyyyMMddHHmmss").format(Calendar.getInstance().getTime());

	// Cordova values.
	private CordovaWebView webView;
	private CallbackContext callbackCtx;

	// Android values.
	private BluetoothAdapter btAdapter;
	private Hashtable<String, String> btPaired;
	private Activity activity;

	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);
		this.activity = cordova.getActivity();
		this.webView = webView;

		// Opens BT communication.
		initPos(CommunicationMode.BLUETOOTH);
	}

	private void initPos(CommunicationMode mode) {
		this.sdkListener = new MyPosListener();
		this.sdk = QPOSService.getInstance(mode);

		// Assigns cordova context.
		this.sdk.setConext(cordova.getActivity());

		// Retreives thread.
		this.sdk.initListener(new Handler(Looper.myLooper()), this.sdkListener);

		// Gets current bt adapter.
		this.btAdapter = BluetoothAdapter.getDefaultAdapter();
		this.btPaired = BluetoothPort.getPairedDevice(this.btAdapter);
	}

	@JavascriptInterface
	public void callback(String method, String result) {
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				webView.loadUrl("javascript:window.cordovaResult('" + method + "','" + result + "')");
			}
		});
	}

	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

		return super.execute(action, args, callbackContext);
	}

	@Override
	public boolean execute(String action, CordovaArgs args, CallbackContext callbackContext) throws JSONException {
		this.callbackCtx = callbackContext;

		// Function selector.
		switch (action) {
		case "connectBluetoothDevice":
			connectBluetoothDevice(args);
			return true;
		case "setAmount":
			setAmount(args);
			return true;
		case "doTrade":
			doTrade(args);
			return true;
		case "requestSetPin":
			break;
		default:
			return false;
		}

		return true;
	}

	// Payment functions.
	public void setAmount(CordovaArgs args) throws JSONException {
		// Parse
		this.amount = args.getString(0);
		this.cashback = args.getString(1);
		this.transactType = parseTransacionType(args.getString(2));

		// Despliega info.
		this.sdk.setPosDisplayAmountFlag(true);
		// Asigna total.
		this.sdk.setAmount(this.amount, this.cashback, this.currencyCodeMex, this.transactType);

		this.callbackCtx.success("Se agregó la cantidad de forma exitosa.");
	}

	public void doTrade(CordovaArgs args) throws JSONException {
		int timeout = args.getInt(0);
		this.sdk.doTrade(timeout);

		// this.callbackCtx.success("Comenzó la operación de cobro.");

	}

	// BtFunctions.
	public void connectBluetoothDevice(CordovaArgs args) throws JSONException {
		// Parse
		boolean autoConnect = args.getBoolean(0);
		String macAddress = args.getString(1);

		try {
			this.sdk.connectBluetoothDevice(autoConnect, 30, macAddress);
			this.callbackCtx.success("Se conecto el dispositivo");

		} catch (Exception ex) {
			this.callbackCtx.error(ex.getMessage());
		}

	}
	// public void

	// Utils.
	public TransactionType parseTransacionType(String transactName) {
		if (transactName.equals("GOODS")) {
			return TransactionType.GOODS;
		} else if (transactName.equals("SERVICES")) {
			return TransactionType.SERVICES;
		} else if (transactName.equals("CASH")) {
			return TransactionType.CASH;
		} else if (transactName.equals("CASHBACK")) {
			return TransactionType.CASHBACK;
		} else if (transactName.equals("INQUIRY")) {
			return TransactionType.INQUIRY;
		} else if (transactName.equals("TRANSFER")) {
			return TransactionType.TRANSFER;
		} else if (transactName.equals("ADMIN")) {
			return TransactionType.ADMIN;
		} else if (transactName.equals("CASHDEPOSIT")) {
			return TransactionType.CASHDEPOSIT;
		} else if (transactName.equals("PAYMENT")) {
			return TransactionType.PAYMENT;
		} else if (transactName.equals("PBOCLOG||ECQ_INQUIRE_LOG")) {
			return TransactionType.PBOCLOG;
		} else if (transactName.equals("SALE")) {
			return TransactionType.SALE;
		} else if (transactName.equals("PREAUTH")) {
			return TransactionType.PREAUTH;
		} else if (transactName.equals("ECQ_DESIGNATED_LOAD")) {
			return TransactionType.ECQ_DESIGNATED_LOAD;
		} else if (transactName.equals("ECQ_UNDESIGNATED_LOAD")) {
			return TransactionType.ECQ_UNDESIGNATED_LOAD;
		} else if (transactName.equals("ECQ_CASH_LOAD")) {
			return TransactionType.ECQ_CASH_LOAD;
		} else if (transactName.equals("ECQ_CASH_LOAD_VOID")) {
			return TransactionType.ECQ_CASH_LOAD_VOID;
		} else if (transactName.equals("CHANGE_PIN")) {
			return TransactionType.UPDATE_PIN;
		} else if (transactName.equals("REFUND")) {
			return TransactionType.REFUND;
		}
		return null;
	}

	// REGION: SDK CALLBACKS.

	class MyPosListener implements QPOSServiceListener {

		@Override
		public void getMifareCardVersion(Hashtable<String, String> arg0) {

		}

		@Override
		public void getMifareFastReadData(Hashtable<String, String> arg0) {
		}

		@Override
		public void getMifareReadData(Hashtable<String, String> arg0) {
		}

		@Override
		public void onAddKey(boolean arg0) {
		}

		@Override
		public void onBluetoothBoardStateResult(boolean arg0) {
		}

		@Override
		public void onBluetoothBondFailed() {
		}

		@Override
		public void onBluetoothBondTimeout() {
		}

		@Override
		public void onBluetoothBonded() {
		}

		@Override
		public void onBluetoothBonding() {
		}

		@Override
		public void onCbcMacResult(String arg0) {
		}

		@Override
		public void onConfirmAmountResult(boolean arg0) {
		}

		@Override
		public void onDeviceFound(BluetoothDevice arg0) {
			String name = arg0.getName();
			String address = arg0.getAddress();

			callback("onDeviceFound", name + address);
		}

		@Override
		public void onDoTradeResult(DoTradeResult arg0, Hashtable<String, String> arg1) {

			try{
				// Creates JSON envelope.
				JSONObject result = new JSONObject();

				if (arg0 == DoTradeResult.NONE) {
					result.put("statusCode", "NONE");
				} else if (arg0 == DoTradeResult.ICC) {
					// ICC payment start.
					sdk.doEmvApp(EmvOption.START);

					result.put("statusCode", "ICC");

				} else if (arg0 == DoTradeResult.NOT_ICC) {
					result.put("statusCode", "NOT_ICC");
				} else if (arg0 == DoTradeResult.BAD_SWIPE) {
					// There was an error while swipping the card.
					result.put("statusCode", "BAD_SWIPE");

				} else if (arg0 == DoTradeResult.MCR) {//
					String content = "swipe card:";
					String formatID = arg1.get("formatID");
					if (formatID.equals("31") || formatID.equals("40") || formatID.equals("37") || formatID.equals("17")
							|| formatID.equals("11") || formatID.equals("10")) {
						String maskedPAN = arg1.get("maskedPAN");
						String expiryDate = arg1.get("expiryDate");
						String cardHolderName = arg1.get("cardholderName");
						String serviceCode = arg1.get("serviceCode");
						String trackblock = arg1.get("trackblock");
						String psamId = arg1.get("psamId");
						String posId = arg1.get("posId");
						String pinblock = arg1.get("pinblock");
						String macblock = arg1.get("macblock");
						String activateCode = arg1.get("activateCode");
						String trackRandomNumber = arg1.get("trackRandomNumber");
					} else if (formatID.equals("FF")) {
						String type = arg1.get("type");
						String encTrack1 = arg1.get("encTrack1");
						String encTrack2 = arg1.get("encTrack2");
						String encTrack3 = arg1.get("encTrack3");
						content += "cardType:" + " " + type + "\n";
						content += "track_1:" + " " + encTrack1 + "\n";
						content += "track_2:" + " " + encTrack2 + "\n";
						content += "track_3:" + " " + encTrack3 + "\n";
					} else {
						String orderID = arg1.get("orderId");
						String maskedPAN = arg1.get("maskedPAN");
						String expiryDate = arg1.get("expiryDate");
						String cardHolderName = arg1.get("cardholderName");
						String serviceCode = arg1.get("serviceCode");
						String track1Length = arg1.get("track1Length");
						String track2Length = arg1.get("track2Length");
						String track3Length = arg1.get("track3Length");
						String encTracks = arg1.get("encTracks");
						String encTrack1 = arg1.get("encTrack1");
						String encTrack2 = arg1.get("encTrack2");
						String encTrack3 = arg1.get("encTrack3");
						String partialTrack = arg1.get("partialTrack");

						String pinKsn = arg1.get("pinKsn");
						String trackksn = arg1.get("trackksn");
						String pinBlock = arg1.get("pinBlock");
						String encPAN = arg1.get("encPAN");
						String trackRandomNumber = arg1.get("trackRandomNumber");
						String pinRandomNumber = arg1.get("pinRandomNumber");
						if (orderID != null && !"".equals(orderID)) {
							content += "orderID:" + orderID;
						}
						content += "formatID" + " " + formatID + ",";
						content += "maskedPAN" + " " + maskedPAN + ",";
						content += "expiryDate" + " " + expiryDate + ",";
						content += "cardHolderName" + " " + cardHolderName + ",";
						content += "pinKsn" + " " + pinKsn + ",";
						content += "trackksn" + " " + trackksn + ",";
						content += "serviceCode" + " " + serviceCode + ",";
						content += "track1Length" + " " + track1Length + ",";
						content += "track2Length" + " " + track2Length + ",";
						content += "track3Length" + " " + track3Length + ",";
						content += "encTracks" + " " + encTracks + ",";
						content += "encTrack1" + " " + encTrack1 + ",";
						content += "encTrack2" + " " + encTrack2 + ",";
						content += "encTrack3" + " " + encTrack3 + ",";
						content += "partialTrack" + " " + partialTrack + ",";
						content += "pinBlock" + " " + pinBlock + ",";
						content += "encPAN: " + encPAN + ",";
						content += "trackRandomNumber: " + trackRandomNumber + ",";
						content += "pinRandomNumber:" + " " + pinRandomNumber + "";

						result.put("statusCode", "MCR");
						result.put("result", content);
					}
				} else if ((arg0 == DoTradeResult.NFC_ONLINE) || (arg0 == DoTradeResult.NFC_OFFLINE)) {
					// NFC Not supported.
					result.put("statusCode", "NOT_SUPPORTED");
				} else if ((arg0 == DoTradeResult.NFC_DECLINED)) {
					// Error al realizar pago con NFC.
					result.put("statusCode", "NFC_DECLINED");
				} else if (arg0 == DoTradeResult.NO_RESPONSE) {
					// No response
					result.put("statusCode", "NO_RESPONSE");
				}

				callback("onDoTradeResult", result.toString());
			}
			catch(JSONException ex) {
				
			}
		}

		@Override
		public void onEmvICCExceptionData(String arg0) {
		}

		@Override
		public void onEncryptData(String arg0) {
		}

		@Override
		public void onError(Error arg0) {
			if (arg0 == Error.CMD_NOT_AVAILABLE) {
				// Comando no soportado.
			} else if (arg0 == Error.TIMEOUT) {
				// Timeout
			} else if (arg0 == Error.DEVICE_RESET) {
				// Se reinició el dispositivo.
			} else if (arg0 == Error.UNKNOWN) {
				// Desconocido.
			} else if (arg0 == Error.DEVICE_BUSY) {
				// Device is working.
			} else if (arg0 == Error.INPUT_OUT_OF_RANGE) {
				// Values out of range.
			} else if (arg0 == Error.INPUT_INVALID_FORMAT) {

			} else if (arg0 == Error.INPUT_ZERO_VALUES) {

			} else if (arg0 == Error.INPUT_INVALID) {

			} else if (arg0 == Error.CASHBACK_NOT_SUPPORTED) {

			} else if (arg0 == Error.CRC_ERROR) {

			} else if (arg0 == Error.COMM_ERROR) {

			} else if (arg0 == Error.MAC_ERROR) {

			} else if (arg0 == Error.CMD_TIMEOUT) {

			}
		}

		@Override
		public void onFinishMifareCardResult(boolean arg0) {
		}

		@Override
		public void onGetCardNoResult(String arg0) {
		}

		@Override
		public void onGetInputAmountResult(boolean arg0, String arg1) {
		}

		@Override
		public void onGetPosComm(int arg0, String arg1, String arg2) {
		}

		@Override
		public void onGetShutDownTime(String arg0) {
		}

		@Override
		public void onGetSleepModeTime(String arg0) {
		}

		@Override
		public void onLcdShowCustomDisplay(boolean arg0) {
		}

		@Override
		public void onOperateMifareCardResult(Hashtable<String, String> arg0) {
		}

		@Override
		public void onPinKey_TDES_Result(String arg0) {
		}

		@Override
		public void onQposDoGetTradeLog(String arg0, String arg1) {
		}

		@Override
		public void onQposDoGetTradeLogNum(String arg0) {
		}

		@Override
		public void onQposDoSetRsaPublicKey(boolean arg0) {
		}

		@Override
		public void onQposDoTradeLog(boolean arg0) {
		}

		@Override
		public void onQposGenerateSessionKeysResult(Hashtable<String, String> arg0) {
		}

		@Override
		public void onQposIdResult(Hashtable<String, String> arg0) {
		}

		@Override
		public void onQposInfoResult(Hashtable<String, String> arg0) {
		}

		@Override
		public void onQposIsCardExist(boolean arg0) {
		}

		@Override
		public void onQposKsnResult(Hashtable<String, String> arg0) {
		}

		@Override
		public void onReadBusinessCardResult(boolean arg0, String arg1) {
		}

		@Override
		public void onReadMifareCardResult(Hashtable<String, String> arg0) {
		}

		@Override
		public void onRequestBatchData(String arg0) {
			String tlv = arg0;
			callback("onRequestBatchData",tlv);
		}

		@Override
		public void onRequestCalculateMac(String arg0) {
		}

		@Override
		public void onRequestDeviceScanFinished() {
		}

		@Override
		public void onRequestDisplay(Display arg0) {

			String msg = "";
			if (arg0 == Display.CLEAR_DISPLAY_MSG) {
			} else if (arg0 == Display.MSR_DATA_READY) {
				// TODO: Comprender esto.
			} else if (arg0 == Display.PLEASE_WAIT) {
				msg = "Por favor espere..";
			} else if (arg0 == Display.REMOVE_CARD) {
				msg = "Remueva la tarjeta";
			} else if (arg0 == Display.TRY_ANOTHER_INTERFACE) {
				msg = "Intente otra forma de contarse";
			} else if (arg0 == Display.PROCESSING) {
				msg = "Procesando";
			} else if (arg0 == Display.INPUT_PIN_ING) {
				msg = "Favor de introducir el PIN";
			} else if (arg0 == Display.MAG_TO_ICC_TRADE) {
				msg = "Por favor de introducir la tarjeta en la terminal";
			} else if (arg0 == Display.CARD_REMOVED) {
				msg = "Se removio la tarjeta";
			}
			callback("onRequestDisplay", msg);
		}

		@Override
		public void onRequestFinalConfirm() {
		}

		@Override
		public void onRequestIsServerConnected() {
			// Validates EmcorPay service reliability.
			sdk.isServerConnected(true);
		}

		@Override
		public void onRequestNoQposDetected() {
			callback("onRequestNoQposDetected", "No se detectó QPos");
		}

		@Override
		public void onRequestOnlineProcess(String arg0) {
			Hashtable<String, String> tlv = sdk.anlysEmvIccData(arg0);

			// Send TLV to ws to retrive ARPC
			String stub_arpc = "5A0A6214672500000000056F5F24032307315F25031307085F2A0201565F34010182027C008407A00000033301018E0C000000000000000002031F009505088004E0009A031406179C01009F02060000000000019F03060000000000009F0702AB009F080200209F0902008C9F0D05D86004A8009F0E0500109800009F0F05D86804F8009F101307010103A02000010A010000000000CE0BCE899F1A0201569F1E0838333230314943439F21031826509F2608881E2E4151E527899F2701809F3303E0F8C89F34030203009F3501229F3602008E9F37042120A7189F4104000000015A0A6214672500000000056F5F24032307315F25031307085F2A0201565F34010182027C008407A00000033301018E0C000000000000000002031F00";

			// Validation process using EmcorPay API.
			String deviceCode = "8A023030";

			// Sends response
			sdk.sendOnlineProcessResult(deviceCode + stub_arpc);

			// Retrieves Tlv
			callback("onRequestOnlineProcess", "Success");
		}

		@Override
		public void onRequestQposConnected() {
			// callbackCtx.success("Que loco viejo");
			callback("onRequestQposConnected", "Se conectó el dispositivo");

			// callback("Comenzó la conexión con Qpos");
		}

		@Override
		public void onRequestQposDisconnected() {
			callback("onRequestQposDisconnected", "Se desconectó el dispositivo");
			// callback("Se perdió la conexion");
		}

		@Override
		public void onRequestSelectEmvApp(ArrayList<String> arg0) {
			// Aquí probablemente se elige si se paga con puntos.

			String[] appNameList = new String[arg0.size()];
			for (int i = 0; i < appNameList.length; ++i) {
				TRACE.d("i=" + i + "," + arg0.get(i));
				appNameList[i] = arg0.get(i);
			}

			// Default.
			sdk.selectEmvApp(0);

			callback("onRequestSelectEmvApp", "Test");
		}

		@Override
		public void onRequestSetAmount() {
			callback("onRequestSetAmount", "Agrega dinero");

			// String total = "100";
			// String cashBack = "100";
			// String countryCode = "484";
			// TrasactionType tranType = TransactionType.GOODS;

			// pos.setPosDisplayAmountFlag(true);
			// pos.setAmount(total, cashBack, countryCode, tranType);
			// if (callbackContext != null) {
			// callbackContext.sendPluginResult(result);
			// callbackContext = null;
			// }
		}

		@Override
		public void onRequestSetPin() {
			callback("onRequestSetPin", "Agregar PIN");

			// TODO: Saber cómo se usa esto.
			// String pin = "";
			// if (pin.length() >= 4 && pin.length() <= 12) {
			// pos.sendPin(pin);
			// }
		}

		@Override
		public void onRequestSignatureResult(byte[] arg0) {
		}

		@Override
		public void onRequestTime() {
			// Envia hora al terminal.
			sdk.sendTime(terminalTime);
		}

		@Override
		public void onRequestTransactionLog(String arg0) {
		}

		@Override
		public void onRequestTransactionResult(TransactionResult arg0) {

			if (arg0 == TransactionResult.APPROVED) {
				// TRACE.d("TransactionResult.APPROVED");
				// String message = "transaction_approved" + "\n" + "amount" + ": $" + amount +
				// "\n";
				// if (!cashbackAmount.equals("")) {
				// message += "cashbackAmount" + ": INR" + cashbackAmount;
				// }
				callback("onRequestTransactionResult", "Aprobado");
			} else if (arg0 == TransactionResult.TERMINATED) {
			} else if (arg0 == TransactionResult.DECLINED) {
			} else if (arg0 == TransactionResult.CANCEL) {
			} else if (arg0 == TransactionResult.CAPK_FAIL) {
			} else if (arg0 == TransactionResult.NOT_ICC) {
			} else if (arg0 == TransactionResult.SELECT_APP_FAIL) {
			} else if (arg0 == TransactionResult.DEVICE_ERROR) {
			} else if (arg0 == TransactionResult.TRADE_LOG_FULL) {
			} else if (arg0 == TransactionResult.CARD_NOT_SUPPORTED) {
			} else if (arg0 == TransactionResult.MISSING_MANDATORY_DATA) {
			} else if (arg0 == TransactionResult.CARD_BLOCKED_OR_NO_EMV_APPS) {
			} else if (arg0 == TransactionResult.INVALID_ICC_DATA) {
			} else if (arg0 == TransactionResult.FALLBACK) {
			} else if (arg0 == TransactionResult.NFC_TERMINATED) {
			} else if (arg0 == TransactionResult.CARD_REMOVED) {
			}
		}

		@Override
		public void onRequestUpdateKey(String arg0) {
		}

		@Override
		public void onRequestUpdateWorkKeyResult(UpdateInformationResult arg0) {
		}

		@Override
		public void onRequestWaitingUser() {
			callback("onRequestWaitingUser", "WAITING.");
		}

		@Override
		public void onReturnApduResult(boolean arg0, String arg1, int arg2) {
		}

		@Override
		public void onReturnBatchSendAPDUResult(LinkedHashMap<Integer, String> arg0) {
		}

		@Override
		public void onReturnCustomConfigResult(boolean arg0, String arg1) {
		}

		@Override
		public void onReturnDownloadRsaPublicKey(HashMap<String, String> arg0) {
		}

		@Override
		public void onReturnGetEMVListResult(String arg0) {
		}

		@Override
		public void onReturnGetPinResult(Hashtable<String, String> arg0) {
			String pinBlock = arg0.get("pinBlock");
			String pinKsn = arg0.get("pinKsn");
			String content = "";

			content += "pinKsn:" + " " + pinKsn + "\n";
			content += "pinBlock:" + " " + pinBlock + "\n";
			callback("onReturnGetPinResult", content);
		}

		@Override
		public void onReturnGetQuickEmvResult(boolean arg0) {
		}

		@Override
		public void onReturnNFCApduResult(boolean arg0, String arg1, int arg2) {
		}

		@Override
		public void onReturnPowerOffIccResult(boolean arg0) {
		}

		@Override
		public void onReturnPowerOffNFCResult(boolean arg0) {
		}

		@Override
		public void onReturnPowerOnIccResult(boolean arg0, String arg1, String arg2, int arg3) {
		}

		@Override
		public void onReturnPowerOnNFCResult(boolean arg0, String arg1, String arg2, int arg3) {
		}

		@Override
		public void onReturnReversalData(String arg0) {
		}

		@Override
		public void onReturnSetMasterKeyResult(boolean arg0) {
		}

		@Override
		public void onReturnSetSleepTimeResult(boolean arg0) {
		}

		@Override
		public void onReturnUpdateEMVRIDResult(boolean arg0) {
		}

		@Override
		public void onReturnUpdateEMVResult(boolean arg0) {
		}

		@Override
		public void onReturnUpdateIPEKResult(boolean arg0) {
		}

		@Override
		public void onReturniccCashBack(Hashtable<String, String> arg0) {
		}

		@Override
		public void onSearchMifareCardResult(Hashtable<String, String> arg0) {
		}

		@Override
		public void onSetBuzzerResult(boolean arg0) {
		}

		@Override
		public void onSetManagementKey(boolean arg0) {
		}

		@Override
		public void onSetParamsResult(boolean arg0, Hashtable<String, Object> arg1) {
		}

		@Override
		public void onSetSleepModeTime(boolean arg0) {
		}

		@Override
		public void onUpdateMasterKeyResult(boolean arg0, Hashtable<String, String> arg1) {
		}

		@Override
		public void onUpdatePosFirmwareResult(UpdateInformationResult arg0) {
		}

		@Override
		public void onVerifyMifareCardResult(boolean arg0) {
		}

		@Override
		public void onWaitingforData(String arg0) {
		}

		@Override
		public void onWriteBusinessCardResult(boolean arg0) {
		}

		@Override
		public void onWriteMifareCardResult(boolean arg0) {
		}

		@Override
		public void transferMifareData(String arg0) {
		}

		@Override
		public void verifyMifareULData(Hashtable<String, String> arg0) {
		}

		@Override
		public void writeMifareULData(String arg0) {

		}

		@Override
		public void onReturnRSAResult(String arg0) {
		}

	}

}
