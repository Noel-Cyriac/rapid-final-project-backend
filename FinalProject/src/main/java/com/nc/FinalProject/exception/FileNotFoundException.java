package com.nc.FinalProject.exception;

import java.io.File;

public class FileNotFoundException extends RuntimeException{
    public FileNotFoundException(String message){
        super(message);
    }
}
