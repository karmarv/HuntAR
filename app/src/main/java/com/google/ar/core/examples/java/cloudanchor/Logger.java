package com.google.ar.core.examples.java.cloudanchor;

import android.util.Log;

import java.io.CharConversionException;

/**
 * Created by Admin on 03.04.2016.
 */
public class Logger {
    private String className;
    public Logger(String loggedFromClass){
        //className = loggedFromClass;
        className = "";
        for (Character c : className.toCharArray()) {
            if (Character.isUpperCase(c)) {
                className += c;
            }
        }
    }

    public void logMethod(String method){
        Log.i("XXM~"+className+"~", method);
    }
    public void logWarning(String warning){
        Log.i("XXW~"+className+"~",warning);
    }
    public void logEmptyLine(){
        Log.i("XXE~"+className+"~", "_----------------------------------------------------------_");
    }


    public void logInfo(String info){
        Log.i("XXI~"+className+"~",info);
    }
    public void logState(String state){
        Log.i("My logger - "+className +"- State",state);
    }
}
