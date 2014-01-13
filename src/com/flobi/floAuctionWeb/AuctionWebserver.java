package com.flobi.floAuctionWeb;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.json.simple.JSONObject;

import com.flobi.floAuction.AuctionScope;
import com.flobi.floAuction.floAuction;
import com.flobi.floAuctionWeb.NanoHTTPD;
import com.google.common.collect.Maps;

class AuctionWebserver extends NanoHTTPD {
	private Map<IHTTPSession, ASWRequest> requests = new HashMap<IHTTPSession, ASWRequest>();
	private Map<IHTTPSession, JSONObject> responses = new HashMap<IHTTPSession, JSONObject>();
	private int bukkitScheduler;

	public AuctionWebserver(String hostname, int port) {
		super(hostname, port);
	}
	
	public AuctionWebserver(int port) {
		super(port);
	}
	
	@Override public void start() throws IOException {
		super.start();
		bukkitScheduler = Bukkit.getScheduler().scheduleSyncRepeatingTask(floAuction.plugin, new Runnable() {
		    public void run() {
		    	processRequests();
		    }
		}, 1L, 1L);
	}
	
	@Override public void stop() {
		Bukkit.getScheduler().cancelTask(bukkitScheduler);
		super.stop();
	}
	
	// NanoHTTPD runs in another thread, so we have to pass the requests to the main Bukkit thread and retrieve the result
	@Override public Response serve(IHTTPSession session) {
		// We have to pass this to Bukkit and wait for a response to be thread responsible with Bukkit objects. 
		ASWRequest request = new ASWRequest();
		request.params = session.getParms();
		requests.put(session, request);
		
		long sleeplength = 10;
		do {
			try {Thread.sleep(sleeplength);} catch (InterruptedException e) {}
			sleeplength *= 1.5;
		} while (sleeplength < 100 && !responses.containsKey(session));
		
		if (requests.containsKey(session)) requests.remove(session);
		
		if (!responses.containsKey(session)) {
			JSONObject json = new JSONObject();
			json.put("error", "Response timeout.");
			responses.remove(session);
			return new AuctionWebserver.Response(json.toJSONString());
		}
		
		JSONObject response = responses.get(session);
		responses.remove(session);
		return new AuctionWebserver.Response(response.toJSONString());
	}
	
	private void processRequests() {
		for (Entry<IHTTPSession, ASWRequest> request : requests.entrySet()) {
			ASWRequest aSWRequest = request.getValue();
			Map<String, String> params = aSWRequest.params;
			IHTTPSession session = request.getKey();
			
			JSONObject json = new JSONObject();
			if (checkVerification(params)) {
				if (params.get("get") != null) {
					json.put("error", "\"get\" has not been implemented.");
				} else if (params.get("auction") != null) {
					json.put("error", "\"auction\" has not been implemented.");
				} else if (params.get("bid") != null) {
					json.put("error", "\"auction\" has not been implemented.");
				} else {
					for (int i = 0; i < AuctionScope.auctionScopesOrder.size(); i++) {
						String auctionScopeId = AuctionScope.auctionScopesOrder.get(i);
						AuctionScope auctionScope = AuctionScope.auctionScopes.get(auctionScopeId);
						JSONObject scopeDetails = new JSONObject();
						scopeDetails.put("id", auctionScopeId);
						scopeDetails.put("name", auctionScope.getName());
						if (auctionScope.getActiveAuction() == null) {
							scopeDetails.put("activeAuctions", 0);
						} else {
							scopeDetails.put("activeAuctions", 1);
						}
						scopeDetails.put("queuedAuctions", auctionScope.getAuctionQueueLength());
						scopeDetails.put("webAuctions", auctionScope.getWebAuctionsLength());
		
						json.put(auctionScopeId, scopeDetails);
					}
				}
			} else {
				json.put("error", "API requires authorization.  Set auth=asdf in the querystring (obviously this isn't a permenant solution).");
			}
			
			responses.put(session, json);
			requests.remove(session);
		}
	}
	
	private boolean checkVerification(Map<String, String> params) {
		String authValue = "asdf";
		String authKey = "auth";
		if (params == null) return false;
		if (!params.containsKey(authKey)) return false;
		return params.get(authKey).equals(authValue);
	}
	
	protected class ASWRequest {
		private Map<String, String> params;
	}
	
}