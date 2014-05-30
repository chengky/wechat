/**
 * wechatdonal
 */
package im;

import im.model.IMMessage;
import im.model.Notice;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.packet.Message;
import org.json.JSONObject;

import qiniu.auth.JSONObjectRet;
import qiniu.io.IO;
import qiniu.io.PutExtra;
import qiniu.utils.Config;
import qiniu.utils.Mac;
import qiniu.utils.PutPolicy;

import com.google.gson.Gson;

import bean.JsonMessage;
import swipeback.SwipeBackActivity;
import tools.DateUtil;
import tools.Logger;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import config.AppActivity;
import config.CommonValue;
import config.MessageManager;
import config.NoticeManager;
import config.XmppConnectionManager;

/**
 * wechat
 *
 * @author donal
 *
 */
public abstract class AChating extends SwipeBackActivity{
	private Chat chat = null;
	protected List<IMMessage> message_pool = new ArrayList<IMMessage>();
	protected String to;
	private static int pageSize = 10;
	private List<Notice> noticeList;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		to = getIntent().getStringExtra("to");
		if (to == null)
			return;
		chat = XmppConnectionManager.getInstance().getConnection()
				.getChatManager().createChat(to, null);
	}
	
	@Override
	protected void onPause() {
		unregisterReceiver(receiver);
		super.onPause();
	}

	@Override
	protected void onResume() {
		message_pool = MessageManager.getInstance(context)
				.getMessageListByFrom(to, 1, pageSize);
		if (null != message_pool && message_pool.size() > 0)
			Collections.sort(message_pool);
		IntentFilter filter = new IntentFilter();
		filter.addAction(CommonValue.NEW_MESSAGE_ACTION);
		registerReceiver(receiver, filter);
		NoticeManager.getInstance(context).updateStatusByFrom(to, Notice.READ);
		super.onResume();

	}
	
	private BroadcastReceiver receiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			Notice notice = (Notice) intent.getSerializableExtra("notice");
			if (CommonValue.NEW_MESSAGE_ACTION.equals(action)) {
				IMMessage message = intent.getParcelableExtra(IMMessage.IMMESSAGE_KEY);
				
				if (!message.getFromSubJid().equals(to)) {
					return;
				}
				message_pool.add(message);
				receiveNewMessage(message);
				refreshMessage(message_pool);
			}
		}

	};
	
	protected abstract void receiveNotice(Notice notice);
	
	protected abstract void receiveNewMessage(IMMessage message);

	protected abstract void refreshMessage(List<IMMessage> messages);
	
	protected List<IMMessage> getMessages() {
		return message_pool;
	}
	
	protected void sendMessage(String messageContent) throws Exception {
		JsonMessage msg = new JsonMessage();
		msg.file = "";
		msg.messageType = CommonValue.kWCMessageTypePlain;
		msg.text = messageContent;
		Gson gson = new Gson();
		String json = gson.toJson(msg);
		
		String time = (System.currentTimeMillis()/1000)+"";
		Message message = new Message();
		message.setProperty(IMMessage.KEY_TIME, time);
		message.setBody(json);
		chat.sendMessage(message);

		IMMessage newMessage = new IMMessage();
		newMessage.setMsgType(1);
		newMessage.setFromSubJid(chat.getParticipant());
		newMessage.setContent(json);
		newMessage.setTime(time);
		message_pool.add(newMessage);
		MessageManager.getInstance(context).saveIMMessage(newMessage);
		refreshMessage(message_pool);
	}
	
	protected void sendMediaMessage(String url) throws Exception {
		JsonMessage msg = new JsonMessage();
		msg.file = url;
		msg.messageType = CommonValue.kWCMessageTypeImage;
		msg.text = "[图片]";
		Gson gson = new Gson();
		String json = gson.toJson(msg);
		
		String time = (System.currentTimeMillis()/1000)+"";
		Message message = new Message();
		message.setProperty(IMMessage.KEY_TIME, time);
		message.setBody(json);
		chat.sendMessage(message);

		IMMessage newMessage = new IMMessage();
		newMessage.setMsgType(1);
		newMessage.setFromSubJid(chat.getParticipant());
		newMessage.setContent(json);
		newMessage.setTime(time);
		message_pool.add(newMessage);
		MessageManager.getInstance(context).saveIMMessage(newMessage);
		refreshMessage(message_pool);
	}
	
	protected Boolean addNewMessage() {
		List<IMMessage> newMsgList = MessageManager.getInstance(context)
				.getMessageListByFrom(to, message_pool.size(), pageSize);
		if (newMsgList != null && newMsgList.size() > 0) {
			message_pool.addAll(newMsgList);
			Collections.sort(message_pool);
			return true;
		}
		return false;
	}
	
	protected int addNewMessage(int currentPage) {
		List<IMMessage> newMsgList = MessageManager.getInstance(context)
				.getMessageListByFrom(to, currentPage, pageSize);
		if (newMsgList != null && newMsgList.size() > 0) {
			message_pool.addAll(newMsgList);
			Collections.sort(message_pool);
			return newMsgList.size();
		}
		return 0;
	}

	protected void resh() {
		refreshMessage(message_pool);
	}
	
	class MsgListener implements MessageListener {

		@Override
		public void processMessage(Chat arg0, Message message) {
			
		}
	}
	
	protected void uploadPhotoToQiniu(String filePath) {
		String bucketName = "dchat";
        PutPolicy putPolicy = new PutPolicy(bucketName);
		Config.ACCESS_KEY = "5e71GMRBlrPS5pjETWcgElaH-uvhGRsWRGMR_Pfs";
        Config.SECRET_KEY = "cqzLJe_hA4YO33Oobp7AF0Fhca4q3EQ2rAfwS2YB";
        Mac mac = new Mac(Config.ACCESS_KEY, Config.SECRET_KEY);
        String auploadToken = null;
		try {
			auploadToken = putPolicy.token(mac);
			Logger.i(auploadToken);
		} catch (Exception e) {
			Logger.i(e);
		}
		String key = IO.UNDEFINED_KEY; 
		PutExtra extra = new PutExtra();
		extra.params = new HashMap<String, String>();
		IO.putFile(auploadToken, key, new File(filePath), extra, new JSONObjectRet() {
			@Override
			public void onProcess(long current, long total) {
				
			}

			@Override
			public void onSuccess(JSONObject resp) {
				String key = resp.optString("hash", "");
				try {
					sendMediaMessage("http://dchat.qiniudn.com/"+key);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			@Override
			public void onFailure(Exception ex) {
				Logger.i(ex.toString());
			}
		});
	}
}
