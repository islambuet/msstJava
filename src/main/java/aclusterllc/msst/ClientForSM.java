package aclusterllc.msst;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static java.lang.String.format;
import static java.lang.Thread.sleep;


public class ClientForSM implements Runnable {
	Logger logger = LoggerFactory.getLogger(ClientForSM.class);
	JSONObject clientInfo;
	ClientForSMMessageQueueHandler clientForSMMessageQueueHandler;
	Selector selector;
	private SocketChannel socketChannel;

	boolean connectedWithSM = false;
	private final long reconnectAfterMillis = 5000;
	ByteBuffer buffer = ByteBuffer.allocate(10240000);
	private int pingCounter = 0;
	private final long pingDelayMillis = 2500;
	private final List<ObserverSMMessage> sMMessageObservers = new ArrayList<>();

	public ClientForSM(JSONObject clientInfo, ClientForSMMessageQueueHandler clientForSMMessageQueueHandler) {
		this.clientInfo=clientInfo;
		this.clientForSMMessageQueueHandler=clientForSMMessageQueueHandler;
		//start3minTpuThread();
	}
//	private void start3minTpuThread() {
//		//send a test signal
//		// You may or may not want to stop the thread here
//		new Thread(() -> {
//			LocalDateTime now = LocalDateTime.now();
//			int secToWait=181-(((now.getMinute())%3)*60+now.getSecond());
//			while (true){
//				try {
//					Thread.sleep(secToWait * 1000);
//					secToWait = 180;
//					if (connectedWithApe)
//					{
//						Connection connection=ConfigurationHelper.getConnection();
//						String query=format("SELECT MAX(total_read) max_total_read FROM statistics WHERE machine_id=%d AND created_at>= (SELECT created_at FROM statistics_counter ORDER BY id DESC LIMIT 1);", clientInfo.getInt("machine_id"));
//						JSONArray queryResult=DatabaseHelper.getSelectQueryResults(connection,query);
//						int maxtput=0;
//						if(queryResult.length()>0){
//							JSONObject maxResult= queryResult.getJSONObject(0);
//							if(maxResult.has("max_total_read")){
//								maxtput=maxResult.getInt("max_total_read")*20;
//							}
//						}
//						byte[] messageBytes = new byte[]{0, 0, 0, 126, 0, 0, 0, 12,(byte) (maxtput >> 24), (byte) (maxtput >> 16), (byte) (maxtput >> 8), (byte) (maxtput)};
//						sendBytes(messageBytes);
//						connection.close();
//					}
//				}
//				catch (Exception ex) {
//					logger.error("Max Tput sender Thread Error");
//					logger.error(CommonHelper.getStackTraceString(ex));
//				}
//			}
//		}).start();
//	}
//	void stopReconnectThread(){
//
//	}

	void startPingThread(){
		pingCounter=0;
		int pingLimit=3;
		new Thread(() -> {
			//System.out.println("Ping Start.MachinedId: "+clientInfo.get("machine_id"));
			while (connectedWithSM) {
				if(pingCounter < pingLimit) {
					//Send pingMessage MSG_ID = 130
					sendBytes(new byte[]{0, 0, 0, (byte) 130, 0, 0, 0, 8});
					pingCounter++;
					//send Text editor notification
					try {
						sleep(pingDelayMillis);
//						JSONObject jsonObject=new JSONObject();
//						jsonObject.put("messageId",130);
//						jsonObject.put("messageLength",8);
//						jsonObject.put("object",this);
//						notifyToApeMessageObservers(jsonObject,new JSONObject());
						sleep(pingDelayMillis);
					}
					catch (InterruptedException ex) {
						logger.error("MESSAGE][PING] Interrupted ping");
						logger.error(HelperCommon.getStackTraceString(ex));
					}
				}
				else {
					logger.error("[MESSAGE][PING] No response for consecutive "+pingLimit+" times.");
					disconnectConnectedSMServer();
					break;
				}
			}

		}).start();
	}
	public void sendBytes(byte[] myByteArray)  {
		if(HelperConfiguration.logSMMessages){
			JSONObject jSONObject=new JSONObject();
			jSONObject.put("Sending",myByteArray);
			logger.info(jSONObject.toString());
		}
		if (!connectedWithSM)
		{
			logger.error("[SEND_MESSAGE_TO_SM][FAIL] Unable to send message. SM Server not connected.");
		}
		else {
			ByteBuffer buf = ByteBuffer.wrap(myByteArray);
			try {
				socketChannel.write(buf);
			}
			catch (IOException ex) {
				logger.error("[SEND_MESSAGE_TO_SM][FAIL] sendBytes Exception.");
				logger.error(HelperCommon.getStackTraceString(ex));
			}
		}
	}
	public void start() {
		new Thread(this).start();//previously was worker
	}
	public void run(){
		while (true){
			connectedWithSM=false;//always false
			try {
				logger.info("[CONNECT] Trying to connect  with SM - "+clientInfo.get("machine_id") );
				selector = Selector.open();
				InetSocketAddress inetSocketAddress = new InetSocketAddress(clientInfo.getString("ip_address"), clientInfo.getInt("port_number"));
				socketChannel = SocketChannel.open(inetSocketAddress);
				socketChannel.configureBlocking(false);
				socketChannel.register(selector, SelectionKey.OP_READ, new StringBuffer());
				logger.info("[CONNECT] connected with: "+socketChannel.getRemoteAddress());
				HelperConfiguration.machinesConnectionStatus.put(clientInfo.getString("machine_id"), 1);
			}
			catch (IOException ex) {
				logger.error("[CONNECT] Connect attempt Failed. Waiting : "+reconnectAfterMillis+ "ms for retry.");
				try {
					sleep(reconnectAfterMillis);
				}
				catch (InterruptedException reconnectEx) {
					logger.error("[CONNECT] Reconnect wait Exception.");
					logger.error(HelperCommon.getStackTraceString(reconnectEx));
				}
				continue;//Retrying
			}
			connectedWithSM=true;
			//Send syncMessage MSG_ID = 116
			sendBytes(new byte[]{0, 0, 0, 116, 0, 0, 0, 8});
			startPingThread();
			while (connectedWithSM){
				try {
					logger.info("waiting for connect or message");
					selector.select();
					Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
					logger.info("waiting for message ends");
					while (iterator.hasNext()) {
						SelectionKey key = iterator.next();
						iterator.remove();
						if(!key.isValid()){
							continue;
						}
						if (key.isReadable()) {
							readReceivedDataFromSM(key);
							logger.info("read Message");
						}
					}
				}
				catch (IOException ex) {
					connectedWithSM=false;
					logger.error("[CONNECT] Connection Closed/Died.");
					logger.error(HelperCommon.getStackTraceString(ex));
				}
			}
			logger.error("[CONNECT] Connection Loop ends.");
		}
	}

	public void readReceivedDataFromSM(SelectionKey key){
		SocketChannel connectedSMServer = (SocketChannel) key.channel();
		buffer.clear();
		int numRead = 0;
		try {
			numRead = connectedSMServer.read(buffer);
		}
		catch (IOException ex) {
			logger.error("[MESSAGE][READ] Message read exception.");
			logger.error(HelperCommon.getStackTraceString(ex));
			disconnectConnectedSMServer();
			return;
		}
		if (numRead == -1) {
			// Remote entity shut the socket down cleanly. Do the same from our end and cancel the channel.
			logger.error("[MESSAGE][READ] Server DisConnected.");
			disconnectConnectedSMServer();
			return;
		}
		byte[] b = new byte[buffer.position()];
		buffer.flip();
		buffer.get(b);
		processReceivedDataFromSM(b);

	}
	public void disconnectConnectedSMServer() {
		try {
			socketChannel.close();
		}
		catch (IOException e) {
			logger.error("[DISCONNECT] Disconnect exception.");
			logger.error(e.toString());
		}
		HelperConfiguration.machinesConnectionStatus.put(clientInfo.getString("machine_id"), 0);
		connectedWithSM=false;
		//worker.interrupt();
	}

	public void processReceivedDataFromSM(byte[] b){
		while (b.length>7){
			JSONObject jsonObject=new JSONObject();
			jsonObject.put("object",this);
			int messageId = HelperCommon.bytesToInt(Arrays.copyOfRange(b, 0, 4));
			int messageLength = HelperCommon.bytesToInt(Arrays.copyOfRange(b, 4, 8));

			byte[] bodyBytes = null;
			if(messageLength>(b.length)){
				System.out.println("Invalid data length");
				break;
			}
			if(messageLength > 8) {
				bodyBytes = Arrays.copyOfRange(b, 8, messageLength);
				jsonObject.put("bodyBytes",bodyBytes);
			}
			jsonObject.put("messageId",messageId);
			jsonObject.put("messageLength",messageLength);

			clientForSMMessageQueueHandler.addMessageToBuffer(jsonObject);
			b= Arrays.copyOfRange(b, messageLength, b.length);
		}
	}
//	public void addApeMessageObserver(ApeMessageObserver apeMessageObserver){
//		apeMessageObservers.add(apeMessageObserver);
//	}
//	public void notifyToApeMessageObservers(JSONObject jsonMessage,JSONObject info){
//		//int messageId=jsonMessage.getInt("messageId");
//		for(ApeMessageObserver apeMessageObserver:apeMessageObservers){
//			//System.out.println(apeMessageObserver.getClass().getSimpleName());
//			//limit messageId for others class
//			apeMessageObserver.processApeMessage(jsonMessage,info);
//		}
//	}
	public void processReceivedMessageFromSM(JSONObject jsonMessage) {
		JSONObject info=new JSONObject();
		int messageId=jsonMessage.getInt("messageId");
		int messageLength=jsonMessage.getInt("messageLength");
		System.out.println("Received Message"+jsonMessage);

	}
//
//	@Override
//	public void processHmiMessage(JSONObject jsonMessage, JSONObject info) {
//		try {
//			String request = jsonMessage.getString("request");
//			JSONObject params = jsonMessage.getJSONObject("params");
//			int machine_id=0;
//			if(params.has("machine_id")){machine_id=params.getInt("machine_id");}
//			if (request.equals("forward_ape_message")) {
//				if(machine_id==clientInfo.getInt("machine_id")){
//					int message_id = Integer.parseInt(params.get("message_id").toString());
//					byte[] messageBytes;
//					switch(message_id) {
//						case 115: {
//							int param_id = Integer.parseInt(params.get("param_id").toString());
//							int value = Integer.parseInt(params.get("value").toString());
//							messageBytes = new byte[]{
//									0, 0, 0, 115, 0, 0, 0, 20, 0, 0, 0, 0,
//									(byte) (param_id >> 24), (byte) (param_id >> 16), (byte) (param_id >> 8), (byte) (param_id),
//									(byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) (value)
//							};
//							sendBytes(messageBytes);
//							break;
//						}
//						case 120:{
//							int mode = Integer.parseInt(params.get("mode").toString());
//							messageBytes = new byte[]{
//									0, 0, 0, 120, 0, 0, 0, 9, (byte) mode
//							};
//							sendBytes(messageBytes);
//							break;
//						}
//						case 123:{
//							int device_id = Integer.parseInt(params.get("device_id").toString());
//							int command = Integer.parseInt(params.get("command").toString());
//							int parameter1 = Integer.parseInt(params.get("parameter1").toString());
//							if(device_id==86 && command ==0){
//								//FOR all machine or current machine
//								Connection connection=ConfigurationHelper.getConnection();
//								String query= format("INSERT INTO statistics_counter (`machine_id`) VALUES (%d);",machine_id);
//								query+= format("INSERT INTO statistics_oee (`machine_id`) VALUES (%d);",machine_id);
//								query+=format("INSERT INTO statistics_bins_counter (machine_id,bin_id) SELECT DISTINCT machine_id,bin_id FROM bins WHERE machine_id=%d;",machine_id);
//								DatabaseHelper.runMultipleQuery(connection,query);
//								connection.close();
//							}
//							messageBytes= new byte[]{
//									0, 0, 0, 123, 0, 0, 0, 20,
//									(byte) (device_id >> 24),(byte) (device_id >> 16),(byte) (device_id >> 8),(byte) (device_id),
//									(byte) (command >> 24),(byte) (command >> 16),(byte) (command >> 8),(byte) (command),
//									(byte) (parameter1 >> 24),(byte) (parameter1 >> 16),(byte) (parameter1 >> 8),(byte) (parameter1)
//							};
//							sendBytes(messageBytes);
//							break;
//						}
//
//
//					}
//				}
//			}
//		}
//		catch (Exception ex){
//			logger.error(CommonHelper.getStackTraceString(ex));
//		}
//	}
}
