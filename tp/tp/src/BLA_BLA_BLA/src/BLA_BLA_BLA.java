import com.ericsson.otp.erlang.*;
import java.math.BigInteger;
import langlib.java.*;
import java.util.Arrays;

public class BLA_BLA_BLA extends BotNode {

    public void OutPutMessageMethod(TestMsg msg)
    {
        System.out.println("TestMsg is message" + msg.get_strParam());
    }



    public BLA_BLA_BLA(String[] args) throws Exception {
        //String[] args2 = new String[5];
        //super("BLA_BLA_BLA", "alex-N550JK", "core@alex-N550JK", "ibot_nodes_srv_topic", "jv");
        //super(new String[] {"BLA_BLA_BLA", "alex-K55A", "core@alex-K55A", "ibot_nodes_srv_connector", "ibot_nodes_srv_topic", "ibot_nodes_srv_service", "jv"});
	//super(new String[] {"BLA_BLA_BLA", "alex-N550JK", "core@alex-N550JK", "ibot_nodes_srv_connector", "ibot_nodes_srv_topic", "ibot_nodes_srv_service", "jv"});
        super(args);
    }

    public void Action() throws Exception {
        System.out.println("Monitor Start...");

        //this.monitorStart();

	
        

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        this.subscribeToTopic("test_topic_from_py", "OutPutMessageMethod", TestMsg.class);
        System.out.println("subscribeToTopic...");
        try {
            Thread.sleep(5000);
		for(int i=0; i<2; i++) {
			TestMsg msg = new TestMsg();
			msg.set_strParam("message #" + i);
			this.publishMessage("testTopic", msg);
		}

        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        System.out.println("Message was send!");

	
        System.out.println("Test");
        //System.exit(2);
        //throw new IllegalArgumentException("Final speed can not be less than zero");
        //}
    }

    public static void main (String[] args) throws Exception {
	System.out.println("Test params " + Arrays.toString(args));
        BLA_BLA_BLA bla_bla_bla = new BLA_BLA_BLA(args);
        bla_bla_bla.Action();
    }
}
