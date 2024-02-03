package aclusterllc.msst;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Arrays;

public class HelperDatabase {
    static Logger logger = LoggerFactory.getLogger(HelperDatabase.class);
    public static void runMultipleQuery(Connection connection,String query) throws SQLException {
        if(query.length()>0){
            connection.setAutoCommit(false);
            Statement stmt = connection.createStatement();
            stmt.execute(query);
            connection.commit();
            connection.setAutoCommit(true);
            stmt.close();
        }
    }
    public static int runUpdateQuery(Connection connection,String query) throws SQLException {
        int num_row=0;
        if(query.length()>0){
            connection.setAutoCommit(false);
            Statement stmt = connection.createStatement();
            num_row = stmt.executeUpdate(query);
            connection.commit();
            connection.setAutoCommit(true);
            stmt.close();
        }
        return num_row;
    }
    public static JSONArray getSelectQueryResults(Connection connection,String query){
        JSONArray resultsJsonArray = new JSONArray();
        try {
            Statement stmt = connection.createStatement();

            ResultSet rs = stmt.executeQuery(query);
            ResultSetMetaData rsMetaData = rs.getMetaData();
            int numColumns = rsMetaData.getColumnCount();
            while (rs.next())
            {
                JSONObject item=new JSONObject();
                for (int i=1; i<=numColumns; i++) {
                    String column_name = rsMetaData.getColumnName(i);
                    item.put(column_name,rs.getString(column_name));
                }
                resultsJsonArray.put(item);
            }
            rs.close();
            stmt.close();
        }
        catch (Exception ex) {
            logger.error(HelperCommon.getStackTraceString(ex));
        }
        return resultsJsonArray;
    }
    public static JSONObject getSelectQueryResults(Connection connection,String query,String[] keyColumns){
        JSONObject resultJsonObject = new JSONObject();
        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery(query);
            ResultSetMetaData rsMetaData = rs.getMetaData();
            int numColumns = rsMetaData.getColumnCount();
            while (rs.next())
            {
                JSONObject item=new JSONObject();
                for (int i=1; i<=numColumns; i++) {
                    String column_name = rsMetaData.getColumnName(i);
                    item.put(column_name,rs.getString(column_name));
                }
                String key="";
                for(int i=0;i<keyColumns.length;i++)
                {
                    if(i==0){
                        key=rs.getString(keyColumns[i]);
                    }
                    else{
                        key+=("_"+rs.getString(keyColumns[i]));
                    }
                }
                resultJsonObject.put(key,item);
            }
            rs.close();
            stmt.close();
        }
        catch (Exception ex) {
            logger.error(HelperCommon.getStackTraceString(ex));
        }
        return resultJsonObject;
    }
    public static JSONObject getAlarms(Connection connection) {
        String query = "SELECT * FROM alarms";
        return getSelectQueryResults(connection,query,new String[] { "machine_id", "alarm_id", "alarm_type"});
    }
    public static JSONObject getBins(Connection connection) {
        String query = "SELECT * FROM bins";
        return getSelectQueryResults(connection,query,new String[] { "machine_id", "bin_id"});
    }
    public static JSONObject getBinsStateColors(Connection connection) {
        String query = "SELECT * FROM bins_state_colors";
        return getSelectQueryResults(connection,query,new String[] { "name"});
    }
    public static JSONObject getBoards(Connection connection) {
        String query = "SELECT * FROM boards";
        return getSelectQueryResults(connection,query,new String[] { "machine_id", "board_id"});
    }
    public static JSONObject getBoardsIo(Connection connection) {
        String query = "SELECT * FROM boards_io";
        return getSelectQueryResults(connection,query,new String[]{ "id"});
    }
    public static JSONObject getConveyors(Connection connection) {
        String query = "SELECT * FROM conveyors";
        return getSelectQueryResults(connection,query,new String[] { "machine_id", "conveyor_id"});
    }
    public static JSONObject getDevices(Connection connection) {
        String query = "SELECT * FROM devicess";
        return getSelectQueryResults(connection,query,new String[] { "machine_id", "device_id"});
    }
}
