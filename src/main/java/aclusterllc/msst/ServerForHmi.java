package aclusterllc.msst;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.String.format;

public class ServerForHmi implements Runnable {
    private Thread worker;
    Selector selector;
    ServerSocketChannel serverSocketChannel;
    ByteBuffer bufferForReadData = ByteBuffer.allocate(10240000);

    ConcurrentHashMap<SocketChannel, ConnectedHmiClient> connectedHmiClientList = new ConcurrentHashMap<>();
    Logger logger = LoggerFactory.getLogger(ServerForHmi.class);
    final List<ObserverHmiMessage> hmiMessageObservers = new ArrayList<>();
    public ServerForHmi() {
    }
    public void start(){
        logger.info("HMI Server Started");
        worker = new Thread(this);
        try {
            selector = Selector.open();
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress(Integer.parseInt(HelperConfiguration.configIni.getProperty("hmi_server_port"))));
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            worker.start();
        } catch (IOException ex) {
            logger.error(HelperCommon.getStackTraceString(ex));
        }
    }
    public void run() {
        while (true) {
            try {
                selector.select();
                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();
                    if(!key.isValid()){
                        continue;
                    }
                    if(key.isAcceptable()){
                        registerConnectedHmiClient(key);
                    }
                    else if (key.isReadable()) {
                        readReceivedDataFromConnectedHmiClient(key);
                    }
                }
            }
            catch (IOException ex) {
                logger.error(HelperCommon.getStackTraceString(ex));
            }
        }
    }
    public void registerConnectedHmiClient(SelectionKey key){
        try {
            SocketChannel socketChannel=serverSocketChannel.accept();
            socketChannel.configureBlocking(false);
            socketChannel.register(selector, SelectionKey.OP_READ);
            connectedHmiClientList.put(socketChannel,new ConnectedHmiClient(socketChannel));
        } catch (IOException ex) {
            logger.error(HelperCommon.getStackTraceString(ex));
        }
    }
    public void readReceivedDataFromConnectedHmiClient(SelectionKey key) {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        bufferForReadData.clear();
        int numRead = 0;
        try {
            numRead = socketChannel.read(bufferForReadData);
        } catch (IOException e) {
            logger.error(e.toString());
            disconnectConnectedHmiClient(socketChannel);
            return;
        }
        if (numRead == -1) {
            // Remote entity shut the socket down cleanly. Do the same from our end and cancel the channel.
            disconnectConnectedHmiClient(socketChannel);
            return;
        }

        byte[] b = new byte[bufferForReadData.position()];
        bufferForReadData.flip();
        bufferForReadData.get(b);
        ConnectedHmiClient connectedHmiClient=connectedHmiClientList.get(socketChannel);
        if(connectedHmiClient==null){
            logger.error("[DATA_PROCESS] ConnectedHmiClient Not found.");
        }
        else{
            connectedHmiClient.processReceivedData(b);
        }
    }
    class ConnectedHmiClient implements Runnable{
        SocketChannel socketChannel;
        String buffer="";
        Thread thread=new Thread(this);

        public ConnectedHmiClient(SocketChannel socketChannel) {
            this.socketChannel=socketChannel;
            try {
                logger.info("Connected with HmiClient: " + socketChannel.getRemoteAddress());
            }
            catch (Exception ex) {
                logger.error(HelperCommon.getStackTraceString(ex));
            }
            this.start();
        }
        public void start(){
            thread.start();
        }
        public void stop(){
            try {
                thread.interrupt();
                logger.info("Closing Connection with HmiClient: " + socketChannel.getRemoteAddress());
                System.out.println("Closing Connection with HmiClient: " + socketChannel.getRemoteAddress());
                socketChannel.close();
            } catch (IOException ex) {
                logger.error(HelperCommon.getStackTraceString(ex));
            }

        }
        public synchronized void processReceivedData(byte[] b){
            buffer=buffer+new String( b, StandardCharsets.UTF_8 );
            System.out.println("buffer:"+buffer);
            this.notify();
        }
        public void run(){
            synchronized (this){
                try {
                    while (true){
                        wait();
                        processReceivedData();
                    }
                }
                catch (Exception ex) {
                    logger.error(HelperCommon.getStackTraceString(ex));
                }
            }
            System.out.println("Run Ended");
            //notifyToHmiMessageObservers(new JSONObject(),new JSONObject());
        }
        public void processReceivedData(){
            String startTag="<begin>";
            String endTag="</begin>";

            int startPos=buffer.indexOf(startTag);
            int endPos=buffer.indexOf(endTag);
            while (startPos>-1 && endPos>-1){
                if(startPos>0){
                    logger.warn("[DATA_PROCESS][START_POS_ERROR] Message did not started with begin. Data: "+buffer);
                }
                if(startPos>endPos){
                    logger.warn("[DATA_PROCESS][END_POS_ERROR] End tag found before start tag. Data: "+buffer);
                    buffer=buffer.substring(startPos);
                }
                else{
                    String messageString=buffer.substring(startPos+startTag.length(),endPos);
                    try {
                        JSONObject jsonObject = new JSONObject(messageString);
                        processReceivedMessage(jsonObject);
                    }
                    catch (JSONException ex) {
                        logger.error(HelperCommon.getStackTraceString(ex));
                    }
                    buffer=buffer.substring(endPos+endTag.length());
                    //parse
                }
                startPos=buffer.indexOf(startTag);
                endPos=buffer.indexOf(endTag);
            }
        }
        public void processReceivedMessage(JSONObject jsonObject){
            try {
                JSONObject response=new JSONObject();


                String request = jsonObject.getString("request");
                JSONObject params = jsonObject.getJSONObject("params");
                JSONArray requestData = jsonObject.getJSONArray("requestData");

                response.put("request",request);
                response.put("params",params);
                System.out.println(requestData.length());
                //notify
            }
            catch (Exception ex){
                logger.error(HelperCommon.getStackTraceString(ex));
            }

        }
        public void sendMessage(String msg) {
            String startTag="<begin>";
            String endTag="</begin>";
            msg=startTag+msg+endTag;
            ByteBuffer buf = ByteBuffer.wrap(msg.getBytes());
            try {
                while (buf.hasRemaining()){
                    int n=socketChannel.write(buf);
                    if(buf.remaining()>0){
                        logger.info("[DATA_SEND_TO_HMI]] waiting 30 for next send. MSG Len "+msg.length()+" Written bytes: " + n + ", Remaining: " + buf.remaining()+" MSG "+msg.substring(0,30));
                        Thread.sleep(30);
                    }
                }
            }
            catch (Exception ex) {
                logger.error(HelperCommon.getStackTraceString(ex));
            }
        }
    }

    public void disconnectConnectedHmiClient(SocketChannel socketChannel) {
        try {
            ConnectedHmiClient connectedHmiClient=connectedHmiClientList.remove(socketChannel);
            if(connectedHmiClient==null){
                logger.error("[DATA_PROCESS] ConnectedHmiClient Not found.");
            }
            else{
               connectedHmiClient.stop();
            }
        }
        catch (Exception ex) {
            logger.error(HelperCommon.getStackTraceString(ex));
        }
    }
    public void disconnectAllConnectedHmiClient(){
        for (SocketChannel key : connectedHmiClientList.keySet()) {
            this.disconnectConnectedHmiClient(key);
        }
    }
    public void addObserverHmiMessage(ObserverHmiMessage hmiMessageObserver){
        hmiMessageObservers.add(hmiMessageObserver);
    }
    public void notifyToHmiMessageObservers(JSONObject jsonMessage,JSONObject info){
        for(ObserverHmiMessage hmiMessageObserver:hmiMessageObservers){
            hmiMessageObserver.processHmiMessage(jsonMessage,info);
        }
    }
}
