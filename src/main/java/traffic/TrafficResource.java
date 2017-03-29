package traffic;


import common.InternalPools;
import org.apache.hadoop.hbase.*;
import common.jdbc.JdbcConnectionPool;
import org.apache.hadoop.hbase.HRegionInfo;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;

import org.apache.hadoop.hbase.client.Connection;

import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Bytes;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.codehaus.jackson.map.ObjectMapper;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.StringWriter;

import java.util.ArrayList;
import java.sql.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by xuefei_wang on 16-12-13.
 */
@Path("/traffic")
public class TrafficResource extends InternalPools {

    private final ObjectMapper mapper = new ObjectMapper();

    public TrafficResource(Map paramters) {
        super(paramters);
    }


        HashMap metadata = new HashMap();

        Connection hbase = getHbaseConnection();
        HTable table = (HTable)hbase.getTable(TableName.valueOf("TrafficImage"));
        Result rs = table.get(new Get(rowkey.getBytes()));
        byte[] image = rs.getValue("Image".getBytes(), "Data".getBytes());
        String redLightTime = new String(rs.getValue("Image".getBytes(), "RedLightTime".getBytes()));
        String recogMode = new String(rs.getValue("Image".getBytes(), "RecogMode".getBytes()));
        metadata.put("RedLightTime", redLightTime);
        metadata.put("RecogMode", recogMode);
        mapper.writeValue(writer, metadata);

        returnHbaseConnection(hbase);
        return Response.status(200)
                .header("Access-Control-Allow-Origin", "*")
                .header("Content-Type","image/jpeg")
                .header("metadata",writer.toString())
                .entity(image).build();

    @GET
    @Path("/track")
    public Response trackQuery(@QueryParam("start") final String start,
                           @QueryParam("end") final String end,
                           @QueryParam("PlateLicense") final String PlateLicense) throws Exception {
        TransportClient conn = getEsConnection();
        StringWriter writer = new StringWriter();
        HashMap content = new HashMap();

        SearchResponse response = conn.prepareSearch().setIndices("traffic").setTypes("traffic")
                .setQuery(QueryBuilders.termQuery("Plate_License.keyword", PlateLicense))
                .setPostFilter(QueryBuilders.rangeQuery("Time.keyword").gte(start.replace("\"", "")).lte(end.replace("\"", "")))
                .setSize(10000)
                .execute().actionGet();

        for (SearchHit i : response.getHits().getHits()) {
            content.put(i.getSource().get("Time").toString(), i.getSource().get("SBBH").toString());
        }
        mapper.writeValue(writer, content);
        returnEsConnection(conn);
        returnMysqlConnection(connmysql);
        return Response.status(200).header("Access-Control-Allow-Origin", "*").entity(writer.toString()).build();
    }

    @GET
    @Path("/queryTrafficStatistics/Month")
    public Response HbasequeryTrafficStatisticsMonth(
            @QueryParam("start") final String start,
            @QueryParam("end") final String end,
            @QueryParam("deviceId") final String deviceId
    ) throws IOException {
        Connection hbase = getHbaseConnection();
        Table table = hbase.getTable(TableName.valueOf("TrafficInfo"));
        Map<String, Integer> typetotal = new LinkedHashMap<String, Integer>();
        Map<String, Integer> monthcount = new LinkedHashMap<String, Integer>();

        int regionnum = Integer.parseInt(deviceId.substring(deviceId.length() - 1)) % 3;
        String startrow = regionnum + "|" + deviceId + "_" + start.replace("\"", "").split(" ")[0];
        String endrow = regionnum + "|" + deviceId + "_" + end.replace("\"", "").split(" ")[0];
        StringWriter writer = new StringWriter();

        Scan scan = new Scan();
        scan.setStartRow(startrow.getBytes());
        scan.setStopRow(endrow.getBytes());
        ResultScanner rs = table.getScanner(scan);
        for (Result r : rs) {
            String row = new String(r.getRow());
            String date = row.split("\\_")[1];
            String year = date.split("\\-")[0];
            String month = date.split("\\-")[1];
            String day = date.split("\\-")[2];
            String value = Bytes.toString(r.getValue("trafficinfo" .getBytes(), "D" .getBytes()));
            String[] vehicleTypes = value.split("\\|");
            for (String str : vehicleTypes) {
                String[] typenum = str.split("\\:");
                //2017-02|0,100
                if (monthcount.containsKey(year + "-" + month + "|" + typenum[0])) {
                    monthcount.put(year + "-" + month + "|" + typenum[0], monthcount.get(year + "-" + month + "|" + typenum[0]) + Integer.parseInt(typenum[1]));
                } else {
                    monthcount.put(year + "-" + month + "|" + typenum[0], Integer.parseInt(typenum[1]));
                }

                typetotal.put("total", typetotal.getOrDefault("total", 0) + Integer.parseInt(typenum[1]));
                if (typetotal.containsKey(typenum[0])) {
                    typetotal.put(typenum[0], typetotal.get(typenum[0]) + Integer.parseInt(typenum[1]));
                } else {
                    typetotal.put(typenum[0], Integer.parseInt(typenum[1]));
                }
            }
        }
        mapper.writeValue(writer, typetotal);
        mapper.writeValue(writer, monthcount);
        returnHbaseConnection(hbase);
        return Response.status(200).header("Access-Control-Allow-Origin", "*").entity(writer.toString()).build();
    }

    @GET
    @Path("/queryTrafficStatistics/Day")
    public Response HbasequeryTrafficStatisticsDay(
            @QueryParam("start") final String start,
            @QueryParam("end") final String end,
            @QueryParam("deviceId") final String deviceId
    ) throws IOException {
        Connection hbase = getHbaseConnection();
        Table table = hbase.getTable(TableName.valueOf("TrafficInfo"));
        Map<String, Integer> typetotal = new LinkedHashMap<String, Integer>();
        Map<String, Integer> daycount = new LinkedHashMap<String, Integer>();

        int regionnum = Integer.parseInt(deviceId.substring(deviceId.length() - 1)) % 3;
        String startrow = regionnum + "|" + deviceId + "_" + start.replace("\"", "").split(" ")[0];
        String endrow = regionnum + "|" + deviceId + "_" + end.replace("\"", "").split(" ")[0];
        StringWriter writer = new StringWriter();

        Scan scan = new Scan();
        scan.setStartRow(startrow.getBytes());
        scan.setStopRow(endrow.getBytes());
        ResultScanner rs = table.getScanner(scan);
        for (Result r : rs) {
            String row = new String(r.getRow());
            String date = row.split("\\_")[1];
            String value = Bytes.toString(r.getValue("trafficinfo" .getBytes(), "D" .getBytes()));
            String[] vehicleTypes = value.split("\\|");
            for (String str : vehicleTypes) {
                String[] typenum = str.split("\\:");
                daycount.put(date + "|" + typenum[0], daycount.getOrDefault(date + "|" + typenum[0], 0) + Integer.parseInt(typenum[1]));

                typetotal.put("total", typetotal.getOrDefault("total", 0) + Integer.parseInt(typenum[1]));
                if (typetotal.containsKey(typenum[0])) {
                    typetotal.put(typenum[0], typetotal.get(typenum[0]) + Integer.parseInt(typenum[1]));
                } else {
                    typetotal.put(typenum[0], Integer.parseInt(typenum[1]));
                }
            }

        }
        mapper.writeValue(writer, typetotal);
        mapper.writeValue(writer, daycount);
        returnHbaseConnection(hbase);
        return Response.status(200).header("Access-Control-Allow-Origin", "*").entity(writer.toString()).build();
    }

    @GET
    @Path("/queryTrafficStatistics/Hour")
    public Response HbasequeryTrafficStatisticsHour(
            @QueryParam("start") final String start,
            @QueryParam("end") final String end,
            @QueryParam("deviceId") final String deviceId
    ) throws IOException {
        Connection hbase = getHbaseConnection();
        Table table = hbase.getTable(TableName.valueOf("TrafficInfo"));
        Map<String, Integer> typetotal = new LinkedHashMap<String, Integer>();
        Map<String, Integer> daycount = new LinkedHashMap<String, Integer>();

        int regionnum = Integer.parseInt(deviceId.substring(deviceId.length() - 1)) % 3;
        String startrow = regionnum + "|" + deviceId + "_" + start.replace("\"", "").split(" ")[0];
        String endrow = regionnum + "|" + deviceId + "_" + end.replace("\"", "").split(" ")[0];
        StringWriter writer = new StringWriter();

        Scan scan = new Scan();
        scan.setStartRow(startrow.getBytes());
        scan.setStopRow(endrow.getBytes());
        ResultScanner rs = table.getScanner(scan);
        for (Result r : rs) {
            String row = new String(r.getRow());
            String date = row.split("\\_")[1];
            String value = Bytes.toString(r.getValue("trafficinfo" .getBytes(), "17h" .getBytes()));
            String[] vehicleTypes = value.split("\\|");

        }
        mapper.writeValue(writer, typetotal);
        mapper.writeValue(writer, daycount);
        returnHbaseConnection(hbase);
        return Response.status(200).header("Access-Control-Allow-Origin", "*").entity(writer.toString()).build();
    }

}
