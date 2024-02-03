package aclusterllc.msst;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MainGui implements ObserverSMMessage {
    public JTextArea mainTextArea;
    private JButton clearButton;
    private JPanel mainPanel;
    private JScrollPane mainScrollPane;
    private JLabel feedLabel;
    public JLabel pingLabel;
    String projectName="MSST";
    String projectVersion="1.0.1";
    private JCheckBox chk_log_sm_msg;
    Logger logger = LoggerFactory.getLogger(MainGui.class);

    public MainGui() {
        clearButton.addActionListener(actionEvent -> clearMainTextArea());
        chk_log_sm_msg.addActionListener(actionPerformed-> { HelperConfiguration.logSMMessages=chk_log_sm_msg.isSelected();});
    }

    public void clearMainTextArea() {
        mainTextArea.setText("");
    }
    public void appendToMainTextArea(String message){
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
        String displayMessage = String.format("[%s] %s",now.format(dateTimeFormatter),message);
        mainTextArea.append(displayMessage+"\r\n");
    }

    public void startGui() {
        logger.info("=====================================");
        logger.info(projectName+" "+projectVersion);
        logger.info("=====================================");

        JFrame frame = new JFrame(projectName+" "+projectVersion);
        if(Integer.parseInt(HelperConfiguration.configIni.getProperty("java_server_minimized"))==1){
            frame.setState(Frame.ICONIFIED);
        }
        frame.setContentPane(this.mainPanel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    @Override
    public void processSMMessage(JSONObject jsonSMMessage, JSONObject info) {


    }
}