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
 




import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;


/* 

    four main topics to listen in 

       newstore --> new store 
       newscript --> new script 
       relocatestore --> relocate store
       changescript --> update create date of script
      
*/


public class UberEventing extends Thread{



    public static int MAXENT_PER_STORE = 65536;
    public static int MAXENT_PER_PART =   1000;
    public static int PARTS_PER_STORE ;
    public static int MSB_OF_UUID ;
    static {
       PARTS_PER_STORE = MAXENT_PER_STORE / MAXENT_PER_PART ;
       MSB_OF_UUID = (int)(Math.abs(Math.floor(logb(MAXENT_PER_STORE, 16))));
       
    }


    
    private final ConsumerConnector consumer;
    private final String topic;
    private final String cassandraIp;
    private final Cluster cluster;
    private final Session session;
    
    


 
    public UberEventing(String topic, String cassandraIp) throws java.net.UnknownHostException {
	consumer = kafka.consumer.Consumer
	    .createJavaConsumerConnector(createConsumerConfig());
	this.topic = topic;
	this.cassandraIp = cassandraIp;
	



	cluster = Cluster.builder()                                                    
	    .addContactPoint(cassandraIp)
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
	int count = 0;
	long totalTime = 0;
	
	while (it.hasNext()) {
	    String mesg = new String(it.next().message());
	    JSONObject obj = new JSONObject(mesg);
	    String message = obj.getString("message");
	    JSONObject mobj = new JSONObject(message);


	    long startTime = System.nanoTime();

	    
	    if (topic.equals("newstore")) {
		newStore(mobj);
	    }
	    else  {
		if (topic.equals("newscript")) {
		    newScript(mobj);
		}
		else {
		    if (topic.equals("relocatestore")) {
			relocateStore(mobj);
		    }
		    else {
			if (topic.equals("changescript")) {
			    changeScript(mobj);
			}
		    }
		}
	    }


	    long endTime = System.nanoTime();
	    long duration = (endTime - startTime)/1000;  // execution time microseconds
	    count++;
	    totalTime = totalTime + duration;

	    if ( (count % 1000) == 0) {
	    	    System.out.println("total transactions: "+ count);
		    long avtimepertransaction = totalTime / (count * 1000);
	    
		    System.out.println ("average time per transaction: " + avtimepertransaction + " milli-seconds");
	    }
	    

						 
	    
	}
    }
    

    

    
	

    public void  newStore( JSONObject mobj) {


	try {
	    String storeid = mobj.getString("storeid");
	    String streetadd = mobj.getString("streetadd");
	    String zipcode = mobj.getString("zipcode");


	    for (int part = 0 ; part <= PARTS_PER_STORE; part++) {
		String insertST = "INSERT INTO mykeyspace.store (storeid, type, part, streetadd, zipcode) VALUES (\'" +storeid + "\', \'main\'," +  part + ", \'" + streetadd + "\', \'" + zipcode + "\') ";
		System.out.println(insertST);
		session.execute(insertST);
	    }
	}
	catch (org.json.JSONException je) {
	}
	
	
    }


    public void  newScript( JSONObject mobj) {
	
	String storeid = mobj.getString("storeid");
	String scriptid = mobj.getString("scriptid");
	String createdate = mobj.getString("createdate");


	
	String upToNCharacters = scriptid.substring(0, Math.min(scriptid.length(), MSB_OF_UUID));
	upToNCharacters = upToNCharacters.toUpperCase();
	int intValueUUID = Integer.parseInt(upToNCharacters, 16);
	int part = intValueUUID / MAXENT_PER_PART;

	String updateST = "UPDATE mykeyspace.store SET prescription_create_date[\'" + scriptid + "\']  = \'" + createdate + "\' where storeid = \'" + storeid+ "\' and type = \'main\' and part = " + part ;

	//	System.out.println(updateST);
	session.execute(updateST);

    }

    public void  changeScript( JSONObject mobj) {
	
	try {
	    String storeid = mobj.getString("storeid");
	    String scriptid = mobj.getString("scriptid");
	    String createdate = mobj.getString("createdate");
	
	
	
	    String upToNCharacters = scriptid.substring(0, Math.min(scriptid.length(), MSB_OF_UUID));
	    upToNCharacters = upToNCharacters.toUpperCase();
	    int intValueUUID = Integer.parseInt(upToNCharacters, 16);
	    int part = intValueUUID / MAXENT_PER_PART;
	
	    String updateST = "UPDATE mykeyspace.store SET prescription_create_date[\'" + scriptid + "\']  = \'" + createdate + "\' where storeid = \'" + storeid+ "\' and type = \'main\' and part = " + part ;

	    //	    System.out.println(updateST);
	    session.execute(updateST);

	}
	
	catch (org.json.JSONException je) {
	    System.out.println(je);
	}
	
    }

    
    public void  relocateStore( JSONObject mobj) {
	try {
	    String storeid = mobj.getString("storeid");
	    String streetadd = mobj.getString("streetadd");
	    String zipcode = mobj.getString("zipcode");

	    for (int part = 0 ; part <= PARTS_PER_STORE; part++) {
	
		String updateST = "UPDATE mykeyspace.store SET streetadd = \'" + streetadd + "\', zipcode = \'" +zipcode + "\' where storeid = \'"+ storeid + "\' AND type = \'main\' AND part = " + part;
		System.out.println(updateST);
		session.execute(updateST);
		
	    }

	}
	
	catch (org.json.JSONException je) {
	    System.out.println(je);
	}


    }




    // topic, cassandraIP
    

    public static void main(String[] args) {
	try {
	    UberEventing consumerThread = new UberEventing(args[0], args[1]);
	    consumerThread.start();
	}
    	catch (java.net.UnknownHostException uhe) {
	    System.out.println(uhe);
	}

    }

    
    public static double logb( double a, double b )
    {
	return Math.log(a) / Math.log(b);
    }

}
