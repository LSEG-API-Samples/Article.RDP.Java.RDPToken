package com.java;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;


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
	private static  List<NameValuePair> getParamPasswordGrantType() {
		if( _paramsPasswordGrantType == null) {
			 _paramsPasswordGrantType = new ArrayList<NameValuePair>(2);
			_paramsPasswordGrantType.add(new BasicNameValuePair("username", _username));
			_paramsPasswordGrantType.add(new BasicNameValuePair("client_id", _client_id));
			_paramsPasswordGrantType.add(new BasicNameValuePair("grant_type", "password"));
			_paramsPasswordGrantType.add(new BasicNameValuePair("scope", "trapi"));
			_paramsPasswordGrantType.add(new BasicNameValuePair("takeExclusiveSignOnControl", "true"));
		}
		if(_password==null) {
			_password = new String(System.console().readPassword("Enter your password:"));
			_paramsPasswordGrantType.add(new BasicNameValuePair("password", _password));
		}
		return _paramsPasswordGrantType;
	}
	private static  List<NameValuePair> getParamRefreshTokenGrantType() {
		if(  _paramsRefreshTokenGrantType == null) {
			 _paramsRefreshTokenGrantType = new ArrayList<NameValuePair>(2);
			 _paramsRefreshTokenGrantType.add(new BasicNameValuePair("username", _username));
			 _paramsRefreshTokenGrantType.add(new BasicNameValuePair("refresh_token", _refresh_token));
			 _paramsRefreshTokenGrantType.add(new BasicNameValuePair("grant_type", "refresh_token"));
			 _paramsRefreshTokenGrantType.add(new BasicNameValuePair("client_id", _client_id));
			 _paramsRefreshTokenGrantType.add(new BasicNameValuePair("takeExclusiveSignOnControl", "true"));
		} else {
			_paramsRefreshTokenGrantType.set(1, new BasicNameValuePair("refresh_token", _refresh_token) );
		}
		return _paramsRefreshTokenGrantType;
	}
	public static String getToken(HttpClient hc, String username, String clientId)  {
		try {
			if(hc==null) {
				SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(new SSLContextBuilder().build());
				_hc = HttpClients.custom().setSSLSocketFactory(sslsf).build();
			} else {
				_hc = hc;
			}
			_username = username;
			_client_id = clientId;
			//List<NameValuePair> params = new ArrayList<NameValuePair>(2);
			File tokenFile = new File(TOKEN_FILE_NAME);
			if(!tokenFile.exists()) {
				
				/*params.add(new BasicNameValuePair("username", _username));
				params.add(new BasicNameValuePair("password", _password));
				params.add(new BasicNameValuePair("client_id", _client_id));
				params.add(new BasicNameValuePair("grant_type", "password"));
				params.add(new BasicNameValuePair("scope", "trapi"));
				params.add(new BasicNameValuePair("takeExclusiveSignOnControl", "true"));*/
				return requestToken(getParamPasswordGrantType(), false);
			}
			else { //read info from file
				return readTokenFile();
			}
			
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
		return _access_token;
	}
	private static String requestToken(List<NameValuePair> params, boolean useRefreshToken) throws Exception {
		String EDP_VERSION_AUTH = "beta1";
		String url = "https://api.refinitiv.com/auth/oauth2/" +EDP_VERSION_AUTH+ "/token";
		HttpPost httplogin = new HttpPost(url);
		httplogin.setEntity(new UrlEncodedFormEntity(params, "UTF-8"));
	
		//Execute and get the response.
		HttpResponse loginResponse = _hc.execute(httplogin);
		if(!Utils.isSuccessRequestPrnError(loginResponse, "Login fails:")) {
			if(!useRefreshToken)
				System.exit(-1);
			else {//try again with password in case of refresh_token expires
				System.out.println("Request token using refresh_token failed. Try again using password");
				
				/*if(_password==null)
					_password = new String(System.console().readPassword("Enter your password:"));
				params.add(new BasicNameValuePair("username", _username));
				params.add(new BasicNameValuePair("password", _password));
				params.add(new BasicNameValuePair("client_id", _client_id));
				params.add(new BasicNameValuePair("grant_type", "password"));
				params.add(new BasicNameValuePair("scope", "trapi"));
				params.add(new BasicNameValuePair("takeExclusiveSignOnControl", "true"));*/
				
				return requestToken(getParamPasswordGrantType(), false);
			}
		} 
		
		String loginResponseStr = EntityUtils.toString(loginResponse.getEntity());
		JSONObject loginJson = new JSONObject(loginResponseStr);
		int expires_in = Integer.parseInt(loginJson.getString("expires_in"));
		long expiry_tm = System.currentTimeMillis() + (expires_in*1000) - (60*1000);	
		loginJson.put("expiry_tm", expiry_tm);
		_access_token = loginJson.getString("access_token");
		_refresh_token = loginJson.getString("refresh_token");
		System.out.println("login is successful. Writing token info to a file named " + TOKEN_FILE_NAME);
		//write a token file
		boolean success = Utils.writeAFile(TOKEN_FILE_NAME,loginJson.toString(4));
		System.out.println(((success==true)?"Success":"Fail") + " writting " +  TOKEN_FILE_NAME);
		if(!success)
			System.exit(-1);
		return _access_token;
	}
	private static String readTokenFile() {
		//List<NameValuePair> params = new ArrayList<NameValuePair>(2);
		try {            
            BufferedReader br =   new BufferedReader(new FileReader(TOKEN_FILE_NAME));
            String aline; 
            StringBuffer sb = new StringBuffer();
            while ((aline = br.readLine()) != null) {
              sb.append(aline); 
            } 
            br.close();
            JSONObject tokenJson = new JSONObject(sb.toString());
            _refresh_token = tokenJson.getString("refresh_token");
            if(tokenJson.getLong("expiry_tm") > System.currentTimeMillis()) {
    			System.out.println("The token has not expired, use the token in the file.");
    			 _access_token = tokenJson.getString("access_token");
    			
    		} else {
    			System.out.println("Token is expired, request a new token using refresh_token ...");
    			/*params.add(new BasicNameValuePair("username", _username));
    			params.add(new BasicNameValuePair("refresh_token", _refresh_token));
    			params.add(new BasicNameValuePair("grant_type", "refresh_token"));
    			params.add(new BasicNameValuePair("client_id", _client_id));
    			params.add(new BasicNameValuePair("takeExclusiveSignOnControl", "true"));*/
    			
    			_access_token = requestToken(getParamRefreshTokenGrantType(), true);
    		}
        }
        catch(Exception e) {
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
