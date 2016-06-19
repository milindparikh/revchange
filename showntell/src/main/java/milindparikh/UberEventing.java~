package milindparikh;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Iterator;

import org.json.*;
import kafka.consumer.ConsumerConfig;
import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;
import kafka.javaapi.consumer.ConsumerConnector;
 



import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import static org.elasticsearch.index.query.QueryBuilders.*;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.search.SearchHitField;
import org.elasticsearch.search.SearchHits;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;



public class BasicEventing extends Thread{
    private final ConsumerConnector consumer;
    private final String topic;
    private final Client client ;
    private final Cluster cluster;
    private final Session session;
    


 
    public BasicEventing(String topic) throws java.net.UnknownHostException {
	consumer = kafka.consumer.Consumer
	    .createJavaConsumerConnector(createConsumerConfig());
	this.topic = topic;

	    client = TransportClient
		.builder()
		.build()
		.addTransportAddress(
				     new InetSocketTransportAddress(InetAddress
								    .getByName("localhost"), 9300));
	    cluster = Cluster.builder()                                                    
		.addContactPoint("127.0.0.1")
		.build();
	    session = cluster.connect();                                           
	
    }
    
    public static ConsumerConfig createConsumerConfig(){
	Properties props = new Properties();
	props.put("zookeeper.connect", "localhost:2181");
	props.put("group.id", "test_group");
	props.put("zookeeper.session.timeout.ms", "400000");
	props.put("zookeeper.sync.time.ms", "200");
	props.put("auto.commit.interval.ms", "1000");
	return new ConsumerConfig(props);
    }
 
    public void run(){
	Map<String, Integer> topicCountMap = new HashMap<String, Integer>();
	topicCountMap.put(topic, 1);
	Map<String, List<KafkaStream<byte[], byte[]>>> consumerMap = consumer.createMessageStreams(topicCountMap);
	KafkaStream<byte[],byte[]> stream = consumerMap.get(topic).get(0);
	ConsumerIterator<byte[], byte[]> it = stream.iterator();
	while (it.hasNext()) {
	    String mesg = new String(it.next().message());
	    JSONObject obj = new JSONObject(mesg);
	    String message = obj.getString("message");
	    JSONObject mobj = new JSONObject(message);

	    String zipCode = mobj.getString("zipcode");
	    String modelName = mobj.getString("model_name");
	    String rainfall = mobj.getString("rainfall");
	    String dateReported = mobj.getString("datereported");
	    String ts = mobj.getString("ts");


	    String state = getStateForZipCode(zipCode);
	    String rank = getAttribForModelName("rank", modelName);
	    String clazz = getAttribForModelName("class", modelName);



	    String insertBT = "INSERT INTO mykeyspace.basetransaction (datereported, state, class, rank, zip, estimate, modelname, ts) VALUES (\'" +dateReported+"\', \'" +state+"\', " + clazz + ", " + rank + " ," +  zipCode + " , " + rainfall + " , \'" + modelName + "\', \'"+ts+"\')";

	    String updateBC = "UPDATE mykeyspace.basecounter SET numberofestimators = numberofestimators + 1 WHERE datereported = \'"+dateReported+"\' AND  state = \'" +state +"\' AND class = " + clazz + " AND rank = " + rank + "  and estimate = " + rainfall;

	    String selectTopClassRank = "SELECT class, rank FROM mykeyspace.basecounter WHERE datereported = \'"+dateReported+"\' AND state = \'"+state+"\'";
	    

	    System.out.println(insertBT);
	    System.out.println(updateBC);
	    System.out.println(selectTopClassRank);
	    insertIntoCassandra(dateReported, state, insertBT, updateBC, selectTopClassRank);
	    
	}
    }

    
    public void insertIntoCassandra(String dateReported, String state, String insertBT, String updateBC, String selectTopClassRank) {
	session.execute(insertBT);
	session.execute(updateBC);

	ResultSet rs = session.execute(selectTopClassRank);
	Row row = rs.one();
	int topClass = row.getInt("class");
	int topRank = row.getInt("rank");

	String selectTopEstimates = "SELECT estimate, numberofestimators from mykeyspace.basecounter WHERE datereported = \'"+dateReported+"\' AND state = \'"+state+"\' AND class = "+topClass+" AND rank = "+topRank;
	
	ResultSet rs2 = session.execute(selectTopEstimates);
	Iterator<Row> iter2 = rs2.iterator();
	Row row2;
	
	while(iter2.hasNext()) {
	    row2 = iter2.next();
	    String insertSC = "INSERT INTO mykeyspace.sortedcounter (datereported, state, class, rank, numberofestimators, estimate) VALUES ( \'" + dateReported + "\' , \'"+state+"\' , " +topClass + " , " + topRank + " , " + row2.getLong("numberofestimators") + " , " + row2.getInt("estimate") + ")";
	    System.out.println(insertSC);
	    session.execute(insertSC);
	}

	int bestestimate = getBestEstimate (dateReported, state);
	String insertBE = "INSERT INTO mykeyspace.bestestimate (datereported, state, estimate) VALUES ( \'"+dateReported+"\' , \'"+state+ "\' , "+bestestimate + " )";
	System.out.println(insertBE);
	session.execute(insertBE);
	
    }
    

    
    public String getAttribForModelName(String attrib, String modelName) {
	QueryBuilder matchqb = matchQuery(  "model_name",  modelName);
	
	SearchResponse response = client.prepareSearch("test")
	    .setTypes("predictor")
	    .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
	    .setQuery(matchqb)                 // Query
	    .addFields(attrib)
	    .setFrom(0).setSize(60).setExplain(true)
	    .execute()
	    .actionGet();
	
	SearchHits hits = response.getHits();
	SearchHit searchHit = hits.getAt(0);
		
	Map<String, SearchHitField> responseFields = searchHit.getFields();
	Set<String> keys = responseFields.keySet();
	SearchHitField field = responseFields.get(attrib);
	
	if (field != null) {
	    return (String) field.getValues().get(0);
	}
	else {
	    return "";
	}
    }
	

    

    
    public String getStateForZipCode(String zipCode) {

	QueryBuilder matchqb = matchQuery(  "address.zipcode",  zipCode);
	QueryBuilder qb = nestedQuery("address", matchqb);
	
	SearchResponse response = client.prepareSearch("test")
	    .setTypes("state")
	    .setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
	    .setQuery(qb)                 // Query
	    .addFields("state")
	    .setFrom(0).setSize(60).setExplain(true)
	    .execute()
	    .actionGet();
	
	SearchHits hits = response.getHits();
	SearchHit searchHit = hits.getAt(0);
		
	Map<String, SearchHitField> responseFields = searchHit.getFields();
	Set<String> keys = responseFields.keySet();
	SearchHitField field = responseFields.get("state");
	
	if (field != null) {
	    return (String) field.getValues().get(0);
	}
	else {
	    return "";
	}
    }



    public  int getBestEstimate ( String dateReported, String state)  {
	if (isTie(   dateReported,  state)) {
	    return getBestEstimateWithTie (   dateReported,  state) ;
	}
	else {
	    return getBestEstimateWithoutTie (   dateReported,  state) ;
	}
    }

    public  int getBestEstimateWithoutTie ( String dateReported, String state)  {
	int clazz;
	int rank;
	int numberofestimators;
	int estimate;
	
	String CQLStmt = "SELECT class, rank, numberofestimators, estimate FROM mykeyspace.sortedcounter where datereported = \'" + dateReported + "' AND state = \'" + state + "\'";
	ResultSet rs = session.execute(CQLStmt); 
	Row row = rs.one();
	Row row2 = null;
	
	if (row != null) {
	    estimate = row.getInt("estimate");
	    return estimate;
	}
	else {
	    return -1;
	}
    }

    public  int getBestEstimateWithTie ( String dateReported, String state)  {
	int clazz;
	int rank;
	int numberofestimators;
	int estimate;
	
	String CQLStmt = "SELECT class, rank, numberofestimators, estimate FROM mykeyspace.sortedcounter where datereported = \'" + dateReported + "' AND state = \'" + state + "\'";
	ResultSet rs = session.execute(CQLStmt); 
	Row row = rs.one();

	String CQLStmt2 ;
	String CQLStmt3;
	
	
	if (row != null) {

	    clazz = row.getInt("class");
	    rank = row.getInt("rank");
	    numberofestimators = row.getInt("numberofestimators");
	    CQLStmt2 = "SELECT class, rank, numberofestimators, estimate FROM mykeyspace.sortedcounter where datereported = \'" + dateReported + "' AND state = \'" + state + "\' AND " + " class = " +clazz+"  AND rank = "+rank+ " AND numberofestimators = " +numberofestimators;

	    //	    System.out.println(CQLStmt2);
	    
	    
	    ResultSet rs2 = session.execute(CQLStmt2);
	    Iterator<Row> iter = rs2.iterator();
	    ArrayList arlist = new ArrayList();
	    
	    
	    while (iter.hasNext()) {
		row = iter.next();
		arlist.add(new Integer(row.getInt("estimate")));
	    }


	    CQLStmt3 = "SELECT  estimate FROM mykeyspace.basetransaction where datereported = \'" + dateReported + "' AND state = \'" + state + "\' AND " + " class = " +clazz+"  AND rank = "+rank;
	    ResultSet rs3 = session.execute(CQLStmt3);
	    Iterator<Row> iter3 = rs3.iterator();

	    while (iter3.hasNext()) {
		row = iter3.next();

		Integer iEstimate = new Integer(row.getInt("estimate"));
		
		Iterator<Integer> iter2 = arlist.iterator();
		while (iter2.hasNext()) {
		    if(iter2.next().equals(iEstimate)) {
			return iEstimate;
		    }
		}
	    }
	    return -1;
	    
	}
	else {
	    return -1;
	}
	
    }

    public  boolean isTie( String dateReported, String state) {
	int clazz;
	int rank;
	int numberofestimators;
	int estimate;
	
	String CQLStmt = "SELECT class, rank, numberofestimators, estimate FROM mykeyspace.sortedcounter where datereported = \'" + dateReported + "' AND state = \'" + state + "\'";
	ResultSet rs = session.execute(CQLStmt); 
	Row row = rs.one();
	Row row2 = null;
	
	if (row != null) {
	    clazz = row.getInt("class");
	    rank = row.getInt("rank");
	    numberofestimators = row.getInt("numberofestimators");
	    estimate = row.getInt("estimate");
	    row2 = rs.one();
	    if (row2 != null) {
		if (row2.getInt("class") == clazz && (row2.getInt("rank") == rank)) {
		    if (row2.getInt("numberofestimators") == numberofestimators) {
			return true;
		    }
		    else {
			return false;
		    }
		}
		else {
		    return false;
		}
	    }
	    else {
		return false;
	    }
	}
	else {
	    return false;
	}
    }

    public static void main(String[] args) {
	try {
	    BasicEventing consumerThread = new BasicEventing("test");
	    consumerThread.start();
	}
    	catch (java.net.UnknownHostException uhe) {
	    System.out.println(uhe);
	}

    }
}
