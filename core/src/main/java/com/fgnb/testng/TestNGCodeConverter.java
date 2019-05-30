package com.fgnb.testng;

import com.fgnb.model.action.*;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by jiangyitao.
 */
@Data
@Accessors(chain = true)
public class TestNGCodeConverter {

    private final Map<Integer, Method> methodMap = new HashMap();

    private Integer deviceTestTaskId;
    private List<GlobalVar> globalVars;
    private String deviceId;
    private Integer port;
    private String testClassName;
    private Action actionTree;
    private String basePackagePath;
    private String ftlFileName;
    private Boolean isBeforeSuite;
    private Integer platform;

    /**
     * 转换为testng代码
     *
     * @return
     */
    public String convert() throws Exception {
        //递归遍历出action里所有的action并封装到Method里
        walk(actionTree);

        Map<String, Object> dataModel = new HashMap();
        dataModel.put("globalVars", globalVars);
        dataModel.put("methods", methodMap.values());
        dataModel.put("testClassName", testClassName);
        dataModel.put("deviceId", deviceId);
        dataModel.put("port", port);

        dataModel.put("deviceTestTaskId", deviceTestTaskId);
        dataModel.put("testcaseId", actionTree.getId());

        dataModel.put("isBeforeSuite", isBeforeSuite);
        dataModel.put("platform", platform);

        //testng @Test @BeforeSuite注解下调用的方法
        StringBuilder testMethod = new StringBuilder("m_" + actionTree.getId() + "(");
        List<Param> actionParams = actionTree.getParams();
        //如果有参数 则都传入null
        if (!CollectionUtils.isEmpty(actionParams)) {
            testMethod.append(actionParams.stream().map(i -> "null").collect(Collectors.joining(",")));
        }
        testMethod.append(");");
        dataModel.put("testMethod", testMethod.toString());

        return FreemarkerUtil.process(basePackagePath, ftlFileName, dataModel);
    }

    /**
     * 递归遍历出action里所有的action并封装到Method里
     *
     * @param action
     * @return
     */
    private void walk(Action action) {
        Method method = methodMap.get(action.getId());
        if (method == null) {
            method = actionToMethod(action);
            methodMap.put(action.getId(), method);
        }
    }

    /**
     * action转换为Method
     *
     * @param action
     * @return
     */
    private Method actionToMethod(Action action) {
        Method method = new Method();
        method.setClassName(action.getClassName());

        //基础action new的时候是否要传webdriver or macacaclient
        method.setNeedDriver(action.getNeedDriver() == Action.NEED_DRIVER);

        //方法名 m_${actionId}
        method.setMethodName("m_" + action.getId());

        //方法描述
        method.setMethodDescription(action.getName());

        //方法参数
        List<Param> params = action.getParams();
        if (!CollectionUtils.isEmpty(params)) {
            List<String> methodParams = params.stream().map(Param::getName).collect(Collectors.toList());
            method.setMethodParams(methodParams);
        }

        //局部变量
        List<LocalVar> localVars = action.getLocalVars();
        if (!CollectionUtils.isEmpty(localVars)) {
            List<Map<String, String>> vars = localVars.stream().map(localVar -> {
                Map<String, String> var = new HashMap();
                var.put(localVar.getName(), localVar.getValue());
                return var;
            }).collect(Collectors.toList());
            method.setVars(vars);
        }

        //返回值
        method.setHasReturnValue(action.getHasReturnValue() == Action.HAS_RETURN_VALUE);
        method.setReturnValue(action.getReturnValue());

        //步骤里的action
        List<Step> steps = action.getSteps();
        if (!CollectionUtils.isEmpty(steps)) {
            List<MethodStep> methodSteps = steps.stream().map(step -> {
                //步骤
                MethodStep methodStep = new MethodStep();
                //调用方法名
                methodStep.setMethodName("m_" + step.getActionId());
                //步骤号
                methodStep.setStepNumber(step.getNumber());
                //步骤名
                methodStep.setMethodStepName(step.getName());
                //步骤赋值
                methodStep.setEvaluation(step.getEvaluation());
                //步骤传入的参数
                List<ParamValue> paramValues = step.getParamValues();
                if (!CollectionUtils.isEmpty(paramValues)) {
                    List<String> methodParamValues = paramValues.stream().map(ParamValue::getParamValue).collect(Collectors.toList());
                    methodStep.setMethodParamValues(methodParamValues);
                }
                //步骤里的action
                Action stepAction = step.getAction();
                walk(stepAction);
                return methodStep;
            }).collect(Collectors.toList());
            method.setMethodSteps(methodSteps);
        }
        return method;
    }

}