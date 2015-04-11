package langlib.java;

import java.lang.reflect.Method;

/**
 * Created by alex on 4/8/15.
 */
public class CollectionSubscribe {
    private String methodName;
    private Method methodObj;
    private Class<IBotMsgInterface> methodMessageType;

    public CollectionSubscribe(String methodName, Method methodObj, Class<IBotMsgInterface> methodMessageType)
    {
        this.methodName = methodName;
        this.methodObj = methodObj;
        this.methodMessageType = methodMessageType;
    }

    public String get_MethodName()
    {
        return this.methodName;
    }

    public Method get_MethodObj()
    {
        return this.methodObj;
    }

    public Class<IBotMsgInterface> get_MethodMessageType()
    {
        return this.methodMessageType;
    }
}
