package aclusterllc.msst;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Arrays;

public class HelperDatabase {
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
    public static JSONArray getSelectQueryResults(Connection connection,String query) throws SQLException {
        JSONArray resultsJsonArray = new JSONArray();
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
        return resultsJsonArray;
    }
    public static JSONObject getSelectQueryResults(Connection connection,String query,String[] keyColumns) throws SQLException {
        JSONObject resultJsonObject = new JSONObject();
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
        return resultJsonObject;
    }
    public static JSONArray getAlarmsActive(Connection connection,int machineId) throws SQLException {
        String query = String.format("SELECT *,UNIX_TIMESTAMP(date_active) AS date_active_timestamp FROM alarms_active WHERE machine_id=%d ORDER BY id DESC", machineId);
        return getSelectQueryResults(connection,query);
    }
    public static JSONObject getInputsStates(Connection connection,int machineId) throws SQLException {
        String query = String.format("SELECT * FROM inputs_states WHERE machine_id=%d", machineId);
        return getSelectQueryResults(connection,query,new String[] { "machine_id", "input_id"});
    }
    public static int getMachineMode(Connection connection,int machineId) throws SQLException{
        String query = String.format("SELECT machine_mode FROM machines WHERE machine_id=%d", machineId);
        JSONArray modeResult=getSelectQueryResults(connection,query);
        if(modeResult.length()>0){
            return modeResult.getJSONObject(0).getInt("machine_mode");
        }
        return 0;
    }
}
