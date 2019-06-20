package com.java;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Iterator;
import java.util.Vector;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

public class Utils {
	public static boolean isSuccessRequestPrnError(HttpResponse response, String titleError) throws Exception {
		
		if(response.getStatusLine().getStatusCode() != HttpStatus.SC_OK)//fail case
		{
			System.out.println(titleError);
			String jsonStr = EntityUtils.toString(response.getEntity());
			JSONObject json = new JSONObject(jsonStr);
			System.out.println(json.toString(4));
			return false;
		}else {
			
			return true;
		}
		
	}
	
	static boolean writeAFile(String fileName, String line) {
		Vector<String> lines = new Vector<String>();
		lines.add(line);
		return writeAFile(fileName, lines);
	}
	static boolean writeAFile(String fileName, Vector<String> lines) {
		boolean success = false;
		Writer writer = null;

		try {
		    writer = new BufferedWriter(new OutputStreamWriter(
		          new FileOutputStream(fileName)));
		    Iterator<String> aLine = lines.iterator();
		    while (aLine.hasNext()) { 
                writer.write(aLine.next());
                writer.write("\n");
            } 
		    success = true;
		} catch (Exception ex) {
			success = false;
			ex.printStackTrace();
		} finally {
		   try {
			   writer.close();
		   } catch (Exception ex) {
			  ex.printStackTrace();
		   }
		   return success;
		}
		
	}
}
