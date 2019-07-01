package com.java;
//java classes
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

//3rd pary libraries
//Apache Http Components 
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
//JSON in java 
import org.json.JSONObject;


public class EDPToken {
	
	private static String TOKEN_FILE_NAME = "token.txt";
	private static HttpClient _hc;
	private static String _refresh_token;
	private static String _access_token;
	private static String _username;
	private static String _password;
	private static String _client_id;
	private static List<NameValuePair> _paramsPasswordGrantType;
	private static List<NameValuePair> _paramsRefreshTokenGrantType;
	
	//Get the list of parameters for Password Grant Type 
	private static  List<NameValuePair> getParamPasswordGrantType() {
		//If the list of parameters for Password Grant Type is not created, create it.
		if( _paramsPasswordGrantType == null) {
			 _paramsPasswordGrantType = new ArrayList<NameValuePair>(2);
			_paramsPasswordGrantType.add(new BasicNameValuePair("username", _username));
			_paramsPasswordGrantType.add(new BasicNameValuePair("client_id", _client_id));
			_paramsPasswordGrantType.add(new BasicNameValuePair("grant_type", "password"));
			_paramsPasswordGrantType.add(new BasicNameValuePair("scope", "trapi"));
			_paramsPasswordGrantType.add(new BasicNameValuePair("takeExclusiveSignOnControl", "true"));
		}
		//If the password is not got yet, read the password 
		//and add the password to the list of parameters for Password Grant Type
		if(_password==null) {
			_password = new String(System.console().readPassword("Enter your password:"));
			_paramsPasswordGrantType.add(new BasicNameValuePair("password", _password));
		}
		return _paramsPasswordGrantType;
	}
	
	//Get the list of parameters for Refresh Token Grant Type 
	private static  List<NameValuePair> getParamRefreshTokenGrantType() {
		//If the list of parameters for Refresh Token Grant Type is not created, create it.
		if(  _paramsRefreshTokenGrantType == null) {
			 _paramsRefreshTokenGrantType = new ArrayList<NameValuePair>(2);
			 _paramsRefreshTokenGrantType.add(new BasicNameValuePair("username", _username));
			 _paramsRefreshTokenGrantType.add(new BasicNameValuePair("refresh_token", _refresh_token));
			 _paramsRefreshTokenGrantType.add(new BasicNameValuePair("grant_type", "refresh_token"));
			 _paramsRefreshTokenGrantType.add(new BasicNameValuePair("client_id", _client_id));
			 _paramsRefreshTokenGrantType.add(new BasicNameValuePair("takeExclusiveSignOnControl", "true"));
		}
		//Otherwise, update only the Refresh Token
		else {
			_paramsRefreshTokenGrantType.set(1, new BasicNameValuePair("refresh_token", _refresh_token) );
		}
		return _paramsRefreshTokenGrantType;
	}
	
	//The method to get a valid token used in requesting data from EDP 
	public static String getToken(HttpClient hc, String username, String clientId)  {
		 
		try {
			//To create or reuse https connection for REST API used in requesting a new token 
			if(hc==null) {
				SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(new SSLContextBuilder().build());
				_hc = HttpClients.custom().setSSLSocketFactory(sslsf).build();
			} else {
				_hc = hc;
			}
			//Set username and clientId from the input
			_username = username;
			_client_id = clientId;
			File tokenFile = new File(TOKEN_FILE_NAME);
			//If the token file(token.txt) does not exists, 
			//request a new token using Password Grant Type
			if(!tokenFile.exists()) {
				return requestToken(getParamPasswordGrantType(), false);
			}
			//Otherwise, read the token file 
			else { 
				return readTokenFile();
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
		return _access_token;
	}
	//The method to get a new token via REST API 
	private static String requestToken(List<NameValuePair> params, boolean useRefreshToken) throws Exception {
		//Specify the end point for token request
		String EDP_VERSION_AUTH = "beta1";
		String url = "https://api.refinitiv.com/auth/oauth2/" +EDP_VERSION_AUTH+ "/token";
		//Set the parameters to request a new token 
		HttpPost http4Token = new HttpPost(url);
		http4Token.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
		//Make a REST call to get a new token
		HttpResponse tokenResponse = _hc.execute(http4Token);
		//Check if getting a new token is successful(HTTP status code is 200) or not. 
		//if fails, print the error
		if(!Utils.isSuccessRequestPrnError(tokenResponse, "Getting Token Fails:")) {
			//If requesting with Password Grant Type fails, exit the application
			if(!useRefreshToken)
				System.exit(-1);
			//If requesting with Refresh Grant Type fails,
			//request a new token again with Password Grant Type
			else {
				System.out.println("Request token using refresh_token failed. Try again using password");
				return requestToken(getParamPasswordGrantType(), false);
			}
		} 
		//Get the Json Object in the response 
		String tokenResponseStr = EntityUtils.toString(tokenResponse.getEntity());
		JSONObject tokenJson = new JSONObject(tokenResponseStr);
		//Get expires_in which is the period of time that the access_token is still valid
		int expires_in = Integer.parseInt(tokenJson.getString("expires_in"));
		//Calculate the time stamp when the access_token expires 
		long expiry_tm = System.currentTimeMillis() + (expires_in*1000) - (60*1000);	
		//Put the time stamp in the Json Object
		tokenJson.put("expiry_tm", expiry_tm);
		_access_token = tokenJson.getString("access_token");
		_refresh_token = tokenJson.getString("refresh_token");
		System.out.println("Getting token is successful. Writing token info to a file named " + TOKEN_FILE_NAME);
		//Write the Json Object into a token file
		boolean success = Utils.writeAFile(TOKEN_FILE_NAME,tokenJson.toString(4));
		//If writing the token file fails, exit. Otherwise, return the new valid token
		System.out.println(((success==true)?"Success":"Fail") + " writting " +  TOKEN_FILE_NAME);
		if(!success)
			System.exit(-1);
		return _access_token;
	}
	
	//Get the token(access_token) in the file. 
	//If the token is unusable, calls requestToken(..) method to request a new token.
	private static String readTokenFile() {
		//Read the token information in the token file as a String
		try {            
            BufferedReader br =   new BufferedReader(new FileReader(TOKEN_FILE_NAME));
            String aline; 
            StringBuffer sb = new StringBuffer();
            while ((aline = br.readLine()) != null) {
              sb.append(aline); 
            } 
            br.close();
			//Convert the token information String into a JSONObject
            JSONObject tokenJson = new JSONObject(sb.toString());
			//Get Refresh Token in the JSONObject
            _refresh_token = tokenJson.getString("refresh_token");
			//Check if the access_token has been expired or not
	        //If not, get access_token from the JSONObject
            if(tokenJson.getLong("expiry_tm") > System.currentTimeMillis()) {
    			System.out.println("The token has not expired, use the token in the file.");
    			 _access_token = tokenJson.getString("access_token");
    		//If yes, request a new access_token using Refresh Token
    		} else {
    			System.out.println("Token is expired, request a new token using refresh_token ...");
    			_access_token = requestToken(getParamRefreshTokenGrantType(), true);
    		}
        }
        catch(Exception e) {
			//If fails, request a new token again using Password
        	System.out.println("Reading Token file failed, request new token using password ...");
        	try {
        		_access_token = requestToken( getParamPasswordGrantType(), false);
        	}
        	catch(Exception ex) {
        		ex.printStackTrace();
        		System.exit(-1);
        	}
        }
		return _access_token;
    }
	public static void main(String[] args) {
		if(args.length!=2) {
			System.out.println("Usage: java com.java.EDPToken <username> <clientId>" );
			System.exit(-1);
		} else {
			String token =  getToken(null, args[0],args[1]);
			System.out.println("The token is " + token);
		}
	}
}

