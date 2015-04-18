package langlib.java;

import com.ericsson.otp.erlang.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Java Otp Node Connector Class
 */
public abstract class BotNode implements IBotNode {

    /**
     * Current server name machine name
     */
    private String currentServerName;

    /**
     * Java Otp node
     */
    private OtpNode otpNode;

    /**
     * Java Otp node name
     */
    private String otpNodeName;

    /**
     * Otp node mail box
     */
    private OtpMbox otpMboxAsync;

    /**
     * Otp node mail box name
     */
    private String otpMboxNameAsync;

    private OtpMbox otpMbox;

    private String otpMboxName;

    /**
     * Server name core@machine_name
     */
    private String coreNodeName;

    /**
     * Core message publisher module
     */
    private String publisherCoreNode;

    private String serviceCoreNode;

    /**
     * Core node cookies
     */
    private String coreCookie;


    /**
     * Subscribe callback methods collection
     */
    private Map<String, Set<CollectionSubscribe>> subscribeDic;

    /**
     * Async client services collection
     */
    private Map<String, CollectionServiceClient> asyncServiceClientDic;

    /**
     * Async server services collection
     */
    private Map<String, CollectionServiceServer> asyncServiceServerDic;

    /**
     * Operation in action
     */
    private volatile boolean coreIsActive;
    /**
     * Operation in action locker
     */
    private Object coreIsActiveLocker;


    /**
     * Receive message thread from node mailbox
     */
    private Thread receiveMBoxMessageThread = new Thread(new Runnable() {
        public void run()
        {
            while (ok()) {
                try {
                    OtpErlangTuple rMessage = receive(); // receive message from mail box
                    //System.out.println("message was receive...");
                    //invokeSubscribeMethodByTopicName("test_topic", rMessage);
                    if (rMessage != null) // if message exist
                    {
                        String msgType = ((OtpErlangAtom) rMessage.elementAt(0)).toString(); // message type
                        switch (msgType) {
                            // message from subscribe topic
                            case "subscribe":
                                System.out.println("subscribe");
                                String topicName = ((OtpErlangAtom) rMessage.elementAt(1)).toString(); // get topic name
                                OtpErlangTuple subscribeMessage = (OtpErlangTuple) rMessage.elementAt(2); // get topic message

                                invokeSubscribeMethodByTopicName(topicName, subscribeMessage); // invoke callback method with message parameter
                                break;

                            case "call_service_method":
                                String serviceMethodName = ((OtpErlangString) rMessage.elementAt(1)).stringValue();
                                OtpErlangAtom clientMailBoxName = (OtpErlangAtom) rMessage.elementAt(2);
                                OtpErlangAtom clientNodeFullName = (OtpErlangAtom) rMessage.elementAt(3);
                                OtpErlangString clientMethodNameCallback = (OtpErlangString) rMessage.elementAt(4);
                                OtpErlangTuple requestMessageFromClient = (OtpErlangTuple) rMessage.elementAt(5);


                                System.out.println("call_service_method");

                                IBotMsgInterface response = invokeServerServiceMethod(serviceMethodName, requestMessageFromClient);

                                System.out.println("call_service_method response...");


                                OtpErlangObject[] serviceResponseObject = new OtpErlangObject[6];
                                serviceResponseObject[0] = new OtpErlangAtom("response_service_message");
                                serviceResponseObject[1] = clientMailBoxName;
                                serviceResponseObject[2] = clientNodeFullName;
                                serviceResponseObject[3] = clientMethodNameCallback;
                                serviceResponseObject[4] = requestMessageFromClient;
                                serviceResponseObject[5] = response.get_Msg();

                                otpMboxAsync.send(serviceCoreNode, coreNodeName, new OtpErlangTuple(serviceResponseObject));

                                break;

                            case "call_client_service_callback_method":
                                String clientMethodName = ((OtpErlangString) rMessage.elementAt(1)).stringValue();
                                OtpErlangTuple requestMessage = (OtpErlangTuple) rMessage.elementAt(2);
                                OtpErlangTuple responseMessage = (OtpErlangTuple) rMessage.elementAt(3);
                                invokeClientServiceMethodCallback(clientMethodName, requestMessage, responseMessage);
                                break;

                            // system message
                            case "system":
                                String systemAction = ((OtpErlangAtom) rMessage.elementAt(1)).toString(); // system action

                                switch (systemAction) {
                                    case "exit" : set_coreIsActive(false);
                                        break;
                                }

                                //System.out.println("sysMsg");
                                break;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    });

    /**
     * Class constructor
     * @param args Init parameters
     * @throws Exception
     */
    public BotNode(String[] args)
            throws Exception
    {
        this.otpNodeName = args[0]; // init node name
        this.currentServerName = args[1]; // init current server name
        this.coreNodeName = args[2]; // init core node name
        this.otpMboxNameAsync = args[0] + "MBoxAsync"; // init asynchronous mail box name
        this.otpMboxName = args[0] + "MBox"; // init mail box name
        this.publisherCoreNode = args[3]; // init publisher node name
        this.serviceCoreNode = args[4]; // init service node name
        this.coreCookie = args[5]; // init core node cookie

        this.subscribeDic = new HashMap<>(); // init subscribers collection

        this.asyncServiceClientDic = new HashMap<>();
        this.asyncServiceServerDic = new HashMap<>();

        setOtpNode(createNode(otpNodeName, coreCookie));
        setOtpMboxAsync(createMbox(otpMboxNameAsync));
        setOtpMbox(createMbox(otpMboxName));

        this.coreIsActive = true;
        this.coreIsActiveLocker = new Object();

        receiveMBoxMessageThread.start();
    }


    /* ====== Creation Methods Start ====== */

    /**
     * OtpNode create method
     * @param otpNodeName Node name
     * @param coreCookies Core node cookie
     * @return Node object
     * @throws Exception
     */
    public OtpNode createNode(String otpNodeName, String coreCookies) throws Exception
    {
        return new OtpNode(otpNodeName, coreCookies);
    }

    /**
     * OtpMbox create method
     * @param otpMboxName Node main box name
     * @return Node mail box object
     */
    private OtpMbox createMbox(String otpMboxName)
    {
        return getOtpNode().createMbox(otpMboxName);
    }


    /**
     * Subscribe node to topic
     * @param topicName Topic name
     * @param callbackMethodName Callback method name for invoke on message
     * @param callbackMethodMessageType Callback message type class
     * @throws IllegalAccessException
     * @throws NoSuchMethodException
     * @throws InstantiationException
     */
    public void subscribeToTopic(String topicName, String callbackMethodName, Class<? extends IBotMsgInterface> callbackMethodMessageType) throws IllegalAccessException, NoSuchMethodException, InstantiationException {
        OtpErlangObject[] subscribeObject = new OtpErlangObject[4];
        subscribeObject[0] = new OtpErlangAtom("reg_subscr");
        subscribeObject[1] = new OtpErlangAtom(this.otpMboxNameAsync);
        subscribeObject[2] = new OtpErlangAtom(this.otpNodeName + "@" + this.currentServerName);
        subscribeObject[3] = new OtpErlangAtom(topicName);
        this.otpMboxAsync.send(this.publisherCoreNode, this.coreNodeName, new OtpErlangTuple(subscribeObject));
        System.out.println("subscribeToTopic " + topicName);


        Method findMethod = getObjectMethod(callbackMethodName, callbackMethodMessageType);

        CollectionSubscribe collectionSubscribe = new CollectionSubscribe(callbackMethodName, findMethod, callbackMethodMessageType);

        synchronized (this.subscribeDic) { // lock access to map from other thread

            if (this.subscribeDic.containsKey(topicName)) // add subscribe information to dictionary, if
            {
                Set<CollectionSubscribe> collectionSubscribesSet = this.subscribeDic.get(topicName);

                if (!collectionSubscribesSet.contains(collectionSubscribe)) {
                    collectionSubscribesSet.add(collectionSubscribe);
                }

                this.subscribeDic.put(topicName, collectionSubscribesSet);

            } else {
                Set<CollectionSubscribe> collectionSubscribesSet = new HashSet<CollectionSubscribe>();
                collectionSubscribesSet.add(collectionSubscribe);
                this.subscribeDic.put(topicName, collectionSubscribesSet);
            }
        }
    }

    /* ====== Creation Methods End ====== */


    /* ====== Action Methods Start ====== */

    //Message publish method
    public void publishMsg(OtpErlangTuple tuple)
    {
        this.otpMboxAsync.send(this.publisherCoreNode, this.coreNodeName, tuple);
    }

    public void publishMessage(String topicName, Object msg) throws Exception
    {
        System.out.println("publishMessage start... " + topicName);

        IBotMsgInterface msgIn = (IBotMsgInterface)msg;

        System.out.println("publishMessage IBotMsgInterface msgIn = (IBotMsgInterface)msg;... ");

        OtpErlangObject[] subscribeObject = new OtpErlangObject[5];
        subscribeObject[0] = new OtpErlangAtom("broadcast");
        subscribeObject[1] = new OtpErlangAtom(this.otpMboxNameAsync);
        subscribeObject[2] = new OtpErlangAtom(this.otpNodeName + "@" + this.currentServerName);
        subscribeObject[3] = new OtpErlangAtom(topicName);
        subscribeObject[4] = msgIn.get_Msg();
        System.out.println("publishMessage subscribeObject[4] = msgIn.get_Msg();... ");
        this.otpMboxAsync.send(this.publisherCoreNode, this.coreNodeName, new OtpErlangTuple(subscribeObject));
        System.out.println("publishMessage " + topicName);
    }

    public void subscribe(String methodName)
    {
        //this.set_methodName(methodName); DELETE
        try {
            receive();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        }
    }

    public OtpErlangTuple receive() throws IllegalAccessException, NoSuchMethodException, InvocationTargetException, InstantiationException {
        try {
            //OtpErlangTuple par = (OtpErlangTuple) this.otpMboxAsync.receive();
            //invoke(par);
            return (OtpErlangTuple) this.otpMboxAsync.receive();
        } catch (OtpErlangExit otpErlangExit) {
            otpErlangExit.printStackTrace();
        } catch (OtpErlangDecodeException e) {
            e.printStackTrace();
        }

        return null;
    }


    /**
     * Call sync service
     * @param serviceMethodName call service name
     * @param req Request object
     * @param <TResp> Request object type
     * @param <TReq> Response object type
     * @return Response object
     */
    public <TResp extends IBotMsgInterface, TReq extends IBotMsgInterface> TResp syncServiceRequest(String serviceMethodName, TReq req) {
        return null;
    }



    public void asyncServiceRequest(String serviceMethodName, IBotMsgInterface req) throws Exception {
        CollectionServiceClient serviceClient = this.asyncServiceClientDic.get(serviceMethodName);
        OtpErlangObject[] requestServiceObject = new OtpErlangObject[5];
        requestServiceObject[0] = new OtpErlangAtom("request_service_message");
        requestServiceObject[1] = new OtpErlangAtom(this.otpMboxNameAsync);
        requestServiceObject[2] = new OtpErlangAtom(this.otpNodeName + "@" + this.currentServerName);
        requestServiceObject[3] = new OtpErlangString(serviceClient.getClientMethodCallbackName());
        requestServiceObject[4] = new OtpErlangString(serviceMethodName);
        requestServiceObject[5] = req.get_Msg();

        this.otpMboxAsync.send(this.serviceCoreNode, this.coreNodeName, new OtpErlangTuple(requestServiceObject));
    }



    //Getters and Setters

    //otpNode Getter
    public OtpNode getOtpNode()
    {
        return this.otpNode;
    }

    //otpNode Setter
    private void setOtpNode(OtpNode otpNode)
    {
        this.otpNode = otpNode;
    }


    //otpMboxAsync Getter
    public OtpMbox getOtpMboxAsync()
    {
        return  this.otpMboxAsync;
    }

    //otpMboxAsync Setter
    private void setOtpMboxAsync(OtpMbox otpMbox)
    {
        this.otpMboxAsync = otpMbox;
    }

    public OtpMbox getOtpMbox() {
        return otpMbox;
    }

    public void setOtpMbox(OtpMbox otpMbox) {
        this.otpMbox = otpMbox;
    }

    public String getOtpMboxName() {
        return otpMboxName;
    }

    public void setOtpMboxName(String otpMboxName) {
        this.otpMboxName = otpMboxName;
    }

    public boolean ok() {
        boolean isOk;
        synchronized (coreIsActiveLocker)
        {
            isOk = this.coreIsActive;
        }
        return isOk;
    }

    public void set_coreIsActive(boolean isActive) {
        synchronized (coreIsActiveLocker) {
            this.coreIsActive = isActive;
        }
    }

    /* ====== Action Methods End ====== */




    /* ====== Service methods Start ====== */

    public void registerServiceClient(String serverServiceMethodName, String clientServiceMethodName,
                                  Class<? extends IBotMsgInterface> serviceRequest, Class<? extends IBotMsgInterface> serviceResponse) throws IllegalAccessException, NoSuchMethodException, InstantiationException {

        OtpErlangObject[] clientServiceObject = new OtpErlangObject[4];
        clientServiceObject[0] = new OtpErlangAtom("reg_async_client_service_callback");
        clientServiceObject[1] = new OtpErlangAtom(this.otpMboxNameAsync);
        clientServiceObject[2] = new OtpErlangAtom(this.otpNodeName + "@" + this.currentServerName);
        clientServiceObject[3] = new OtpErlangAtom(serverServiceMethodName);
        this.otpMboxAsync.send(this.serviceCoreNode, this.coreNodeName, new OtpErlangTuple(clientServiceObject));
        System.out.println("registerServiceClient " + serverServiceMethodName);

        synchronized (this.asyncServiceClientDic) {

            Method serviceMethod = getServiceClientCallbackObjectMethod(clientServiceMethodName, serviceRequest, serviceResponse);

            if(serviceMethod != null) {

                CollectionServiceClient serviceClient = new CollectionServiceClient(serverServiceMethodName, clientServiceMethodName,
                        serviceRequest, serviceResponse, serviceMethod);

                this.asyncServiceClientDic.put(clientServiceMethodName, serviceClient);
            }
        }

    }


    public void registerServiceServer(String serviceMethodName, Class<? extends IBotMsgInterface> requestType, Class<? extends IBotMsgInterface> responseType) throws IllegalAccessException, NoSuchMethodException, InstantiationException {
        OtpErlangObject[] clientServiceObject = new OtpErlangObject[4];
        clientServiceObject[0] = new OtpErlangAtom("reg_async_server_service_callback");
        clientServiceObject[1] = new OtpErlangAtom(this.otpMboxNameAsync);
        clientServiceObject[2] = new OtpErlangAtom(this.otpNodeName + "@" + this.currentServerName);
        clientServiceObject[3] = new OtpErlangString(serviceMethodName);
        this.otpMboxAsync.send(this.serviceCoreNode, this.coreNodeName, new OtpErlangTuple(clientServiceObject));

        Method serviceMethod = getServiceObjectMethod(serviceMethodName, requestType);

        if (serviceMethod != null) {
            CollectionServiceServer serviceServer = new CollectionServiceServer(serviceMethodName, requestType, responseType, serviceMethod);
            this.asyncServiceServerDic.put(serviceMethodName, serviceServer);
        }
    }

    /* ====== Service methods End ====== */



    /* ====== Invoke callback method Start ====== */

    // Invoke method
    public void invoke(Object... parameters) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException, InstantiationException {
        Method method = this.getClass().getMethod("method name", getParameterClasses(parameters)); // get link to method from current object
        method.invoke(this, parameters); // invoke callback method
    }

    public void invokeCallbackMethod(Method callbackMethod, Class<? extends IBotMsgInterface> obj, Object... parameters) throws InvocationTargetException, IllegalAccessException, InstantiationException, NoSuchMethodException {
        IBotMsgInterface msg = obj.getDeclaredConstructor(OtpErlangTuple.class).newInstance(parameters);
        callbackMethod.invoke(this, msg);
    }

    /**
     * Invoke all methods subscribed to topic
     * @param collectionSubscribeSet Subscribed methods list
     * @param parameters method parameter
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    private void invokeSubscribeSet(Set<CollectionSubscribe> collectionSubscribeSet, Object... parameters) throws InvocationTargetException, IllegalAccessException, InstantiationException, NoSuchMethodException {
        for(CollectionSubscribe collectionSubscribe : collectionSubscribeSet)
        {
            invokeCallbackMethod(collectionSubscribe.get_MethodObj(), collectionSubscribe.get_MethodMessageType(), parameters);
        }
    }

    /**
     * Invoke callback method subscribed to topic
     * @param topicName Topic name
     * @param parameters Method parameter
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    private void invokeSubscribeMethodByTopicName(String topicName, Object... parameters) throws InvocationTargetException, IllegalAccessException, InstantiationException, NoSuchMethodException {
        synchronized (this.subscribeDic) { // lock access to map from other thread
            invokeSubscribeSet(this.subscribeDic.get(topicName), parameters);
        }
    }

    /**
     * Find callback method
     * @param methodName Method name
     * @param methodParams Method parameters
     * @return Callback method link
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws NoSuchMethodException
     */
    private Method getObjectMethod(String methodName, Class<?> methodParams) throws IllegalAccessException, InstantiationException, NoSuchMethodException {
        IBotMsgInterface obj = (IBotMsgInterface) methodParams.newInstance();
        return this.getClass().getMethod(methodName, getParameterClasses(obj));
    }

    /**
     * Find callback service method
     * @param methodName Method name
     * @param methodParamRequest Method request type
     * @param methodParamResponse Method response type
     * @return Callback method link
     * @throws IllegalAccessException
     * @throws InstantiationException
     * @throws NoSuchMethodException
     */
    private Method getServiceClientCallbackObjectMethod(String methodName, Class<?> methodParamRequest, Class<?> methodParamResponse) throws IllegalAccessException, InstantiationException, NoSuchMethodException {
        IBotMsgInterface objReq = (IBotMsgInterface) methodParamRequest.newInstance();
        IBotMsgInterface objResp = (IBotMsgInterface) methodParamResponse.newInstance();
        return this.getClass().getMethod(methodName, getParameterClasses(objReq, objResp));
    }


    private Method getServiceObjectMethod(String methodName, Class<?> methodParamRequest) throws IllegalAccessException, InstantiationException, NoSuchMethodException {
        IBotMsgInterface objReq = (IBotMsgInterface) methodParamRequest.newInstance();
        return this.getClass().getMethod(methodName, getParameterClasses(objReq));
    }

    /**
     * Find method from current class
     * @param parameters Method parameters
     * @return Class
     */
    private Class[] getParameterClasses(Object... parameters) {
        Class[] classes = new Class[parameters.length];
        for (int i=0; i < classes.length; i++) {
            classes[i] = parameters[i].getClass();
        }
        return classes;
    }

    /* ====== Invoke callback method End ====== */

    private void invokeClientServiceMethodCallback(String clientServiceMethodName, Object... parameters)
            throws InvocationTargetException, IllegalAccessException, NoSuchMethodException, InstantiationException {
        CollectionServiceClient client = this.asyncServiceClientDic.get(clientServiceMethodName);
        if(client!=null) {
            IBotMsgInterface request = client.getServiceRequest().getDeclaredConstructor(OtpErlangTuple.class).newInstance(parameters[0]);
            IBotMsgInterface response = client.getServiceResponse().getDeclaredConstructor(OtpErlangTuple.class).newInstance(parameters[1]);
            client.getClientServiceCallback().invoke(this, request, response);
        }
    }

    private <T extends IBotMsgInterface> T invokeServerServiceMethod(String serviceMethodName, Object requestMessage) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException, InstantiationException {
        CollectionServiceServer serviceServer = this.asyncServiceServerDic.get(serviceMethodName);
        IBotMsgInterface request = serviceServer.getServiceRequest().getDeclaredConstructor(OtpErlangTuple.class).newInstance(requestMessage);
        return (T) serviceServer.getServiceCallback().invoke(this, request);
    }
}