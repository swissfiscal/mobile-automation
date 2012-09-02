package org.topq.mobile.robotium_client.impl;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

import net.iharder.Base64;

import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;
import org.topq.mobile.common_mobile.client.enums.HardwareButtons;
import org.topq.mobile.common_mobile.client.interfaces.MobileClintInterface;
import org.topq.mobile.core.AdbController;
import org.topq.mobile.core.GeneralEnums;
import org.topq.mobile.robotium_client.infrastructure.AdbTcpClient;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.Log;


public class RobotiumClientImpl implements MobileClintInterface{

	
	
	private AdbTcpClient tcpClient;
	private static AdbController adb;
	private static Logger logger= null;
	private static boolean getScreenshots = false;
	private static int port = 6100;
	private static String deviceSerial;
	private static String apkLocation = null;
	private static String pakageName = null;
	private static String testClassName = null;
	private static String host = null;
	private static String testName = null;
	private static final String RESULT_STRING ="RESULT";
	
	public RobotiumClientImpl(String configFile,boolean doDeply) throws Exception{
		logger = Logger.getLogger(RobotiumClientImpl.class);
		File file = new File(configFile);
		if(file.exists()){
		Properties pro = new Properties();
		InputStream in = new FileInputStream(file);
		pro.load(in);
		String temeroryProrp = pro.getProperty("Port");
		logger.debug("In Properties file port is:"+temeroryProrp);
		port = Integer.parseInt(temeroryProrp);
		temeroryProrp = pro.getProperty("DeviceSerail");
		logger.debug("In Properties file DeviceSerial is:"+temeroryProrp);
		apkLocation = temeroryProrp;
		temeroryProrp = pro.getProperty("ApkLocation");
		logger.debug("APK location is:"+apkLocation);
		pakageName = pro.getProperty("PakageName");
		logger.debug("Pakage Name is:"+pakageName);
		testClassName = pro.getProperty("TestClassName");
		logger.debug("Test Class Name is:"+testClassName);
		testName = pro.getProperty("TestName");
		logger.debug("Test  Name is:"+testName);
		host = pro.getProperty("Host");
		logger.debug("Host  Name is:"+host);
		deviceSerial=pro.getProperty("DeviceSerail");
		adb = new AdbController(deviceSerial);
		String serverConfFileLocation = pro.getProperty("ServerConfFile");
		if(doDeply){
			adb.installAPK(serverConfFileLocation, temeroryProrp);
		}
		adb.runTestOnDevice(pakageName,testClassName,testName);
		logger.info("Start server on device");
		setPortForwarding();
		tcpClient = new AdbTcpClient(host, port);
		}else{
			Exception e= new Exception("Can't fiend the file:"+file.getAbsolutePath());
			logger.error("Can't fiend the file:"+file.getAbsolutePath());
			throw e;
		}
	}
	
	/**
	 * Send data using the TCP connection & wait for response Parse the response
	 * (make conversions if necessary - pixels to mms) and report
	 * 
	 * @param device
	 * @param data
	 *            serialised JSON object
	 * @throws Exception
	 */
	public String sendData(String command,String... params) throws Exception {	
		String resultValue;
		try {
			JSONObject result =  sendDataAndGetJSonObj(command, params);
			
			if (result.isNull(RESULT_STRING)) {
				logger.error("No data recieved from the device");
				return NO_DATA_STRING;
			}
			 resultValue = (String) result.get(RESULT_STRING);
			if (resultValue.contains(ERROR_STRING)) {
				logger.error(result);
				adb.getScreenShots(getDevice());
			} else if (resultValue.contains(SUCCESS_STRING)) {
				logger.info(result);
			}

		} catch (Exception e) {
			logger.error("Failed to send / receive data", e);
			throw e;
		} 
		if (getScreenshots) {
			adb.getScreenShots(getDevice());
		}
		return resultValue;
	}
	
	public JSONObject sendDataAndGetJSonObj(String command,String... params) throws Exception{
		JSONObject jsonobj = new JSONObject();
		jsonobj.put("Command", command);
		jsonobj.put("Params", params);
		logger.info("Sending command: " + jsonobj.toString());
		JSONObject result = null;
		IDevice device = getDevice();
		logger.info("Send Data to " + device.getSerialNumber());

		try {
			result =  new JSONObject(tcpClient.sendData(jsonobj));
		} catch (Exception e) {
			logger.error("Failed to send / receive data", e);
			throw e;
		} 
		return result;	
	}


	public String launch() throws Exception {
		return sendData("launch");
		
	}

	public String getTextView(int index) throws Exception {
		return sendData("getTextView",Integer.toString(index));
	}

	public String getTextViewIndex(String text) throws Exception {
		String response = sendData("getTextViewIndex",text);
		return response;
	}

	public String getCurrentTextViews() throws Exception {
		return sendData("getCurrentTextViews","a");
	}

	public String getText(int index) throws Exception {
		return sendData("getText",Integer.toString(index));
	}

	public String clickOnMenuItem(String item) throws Exception {
		return sendData("clickOnMenuItem",item);
	}

	public String  clickOnView(int index) throws Exception {
		return sendData("clickOnView",Integer.toString(index));
	}

	public String enterText(int index, String text) throws Exception {
		return sendData("enterText",Integer.toString(index),text);
	}

	public String clickOnButton(int index) throws Exception {
		return sendData("clickOnButton",Integer.toString(index));
	}

	public String clickInList(int index) throws Exception {
		return sendData("clickInList",Integer.toString(index));
	}

	public String clearEditText(int index) throws Exception {
		return sendData("clearEditText",Integer.toString(index));
	}

	public String clickOnButtonWithText(String text) throws Exception {
		return sendData("clickOnButtonWithText",text);
	}

	public String clickOnText(String text) throws Exception {
		return sendData("clickOnText",text);
	}
	@Override
	public String clickOnHardwereButton(HardwareButtons button)
			throws Exception {
		return sendData("clickOnHardware",button.name());
	}

	@Override
	public byte[] pull(String fileName) throws Exception {
		JSONObject jsonObj = sendDataAndGetJSonObj("pull",fileName);
		logger.info("command pull receved" + jsonObj);
		return ((jsonObj.getString("file"))).getBytes("UTF-16LE");
	}

	
	@Override
	public String push(String fileName, String newlocalFileName) throws Exception {
		String result;
		
		File file = new File(fileName);
		BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
		int size = (int) file.length();
		byte[] buffer = new byte[(int) file.length()];
		
		try {
			int n = in.read(buffer, 0, size);
			while (n >= 0) {
				System.out.write(buffer, 0, n);
				n = in.read(buffer, 0, size);
			}
		}
		finally { // always close input stream
			in.close();
		}
		
		
		result =sendData("createFileInServer",newlocalFileName,Base64.encodeBytes(buffer,Base64.URL_SAFE),"true");
		return result;
	}

	public String sendKey(int key) throws Exception {
		return sendData("sendKey",Integer.toString(key));
	}

	public void closeConnection() throws Exception {
		sendData("GodBay");
		tcpClient.closeConnection();

	}
	
	public AdbController getAdb() {
		return adb;
	}

	public void setAdb(AdbController adb) {
		this.adb = adb;
	}
	

	private void setPortForwarding() throws Exception {
		IDevice device = getDevice();
		if (device.getState() == IDevice.DeviceState.ONLINE){
			device.createForward(port, GeneralEnums.SERVERPORT);
		}else{
			Exception e = new Exception("Unable to perform port forwarding - " + deviceSerial + " is not online");
			logger.error(e);
			throw e;
		}
	}
	
	private IDevice getDevice() throws Exception {
		IDevice device = adb.getDevice(deviceSerial);
		if (null == device) {
			Exception e = new Exception("Unable to find device with serial number: " + deviceSerial);
			logger.error(e);
			throw e;
		}
		return device;
	}




}
