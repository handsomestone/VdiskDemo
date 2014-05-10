package com.vdisk.android.example;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

import com.sina.weibo.sdk.auth.Oauth2AccessToken;
import com.sina.weibo.sdk.auth.WeiboAuth;
import com.sina.weibo.sdk.auth.WeiboAuthListener;
import com.sina.weibo.sdk.auth.sso.SsoHandler;
import com.sina.weibo.sdk.demo.AccessTokenKeeper;
import com.sina.weibo.sdk.demo.Constants;
import com.sina.weibo.sdk.exception.WeiboException;
import com.vdisk.android.VDiskAuthSession;
import com.vdisk.android.VDiskDialogListener;
import com.vdisk.net.exception.VDiskDialogError;
import com.vdisk.net.exception.VDiskException;
import com.vdisk.net.exception.VDiskServerException;
import com.vdisk.net.session.AccessToken;
import com.vdisk.net.session.AppKeyPair;
import com.vdisk.net.session.Session.AccessType;
import com.vdisk.net.session.WeiboAccessToken;

public class OAuthActivity extends Activity implements VDiskDialogListener {

	/**
	 * 鏇挎崲涓哄紑鍙戣�搴旂敤鐨刟ppkey锛屼緥濡�16*****960";
	 * 
	 * Replace it to the appkey of developer's application, such as
	 * "16*****960";
	 */
	public static final String CONSUMER_KEY = "2263870802";// TODO

	/**
	 * 鏇挎崲涓哄紑鍙戣�搴旂敤鐨刟pp secret锛屼緥濡�94098*****************861f9";
	 * 
	 * Replace it to the app secret of developer's application, such as
	 * "94098*****************861f9";
	 */
	public static final String CONSUMER_SECRET = "a1b234790ebf3c87c04c9e976588331e";// TODO

	/**
	 * 鏇挎崲涓哄井鍗氱殑access_token. 濡傛灉浣犳兂浣跨敤寰崥token鐩存帴璁块棶寰洏鐨凙PI锛岃繖涓瓧娈典笉鑳戒负绌恒�
	 * 
	 * Replace it to the access_token of WEIBO. If you use weibo token to access
	 * VDisk API, this field should not be null.
	 */
	public static String WEIBO_ACCESS_TOKEN = "";

	/**
	 * 
	 * 姝ゅ搴旇鏇挎崲涓轰笌appkey瀵瑰簲鐨勫簲鐢ㄥ洖璋冨湴鍧�紝瀵瑰簲鐨勫簲鐢ㄥ洖璋冨湴鍧�彲鍦ㄥ紑鍙戣�鐧婚檰鏂版氮寰洏寮�彂骞冲彴涔嬪悗锛岃繘鍏�鎴戠殑搴旂敤--缂栬緫搴旂敤淇℃伅--鍥炶皟鍦板潃"
	 * 杩涜璁剧疆鍜屾煡鐪嬶紝濡傛灉浣跨敤寰洏token鐧婚檰鐨勮瘽锛�搴旂敤鍥炶皟椤典笉鍙负绌恒�
	 * 
	 * The content of this field should replace with the application's redirect
	 * url of corresponding appkey. Developers can login in Sina VDisk
	 * Development Platform and enter "鎴戠殑搴旂敤--缂栬緫搴旂敤淇℃伅--鍥炶皟鍦板潃" to set and view the
	 * corresponding application's redirect url. If you use VDisk token, the
	 * redirect url should not be empty. should not be empty.
	 */
	private static final String REDIRECT_URL = "https://github.com/handsomestone";// TODO

	private Button btn_oauth;
	private CheckBox cbUseWeiboToken;
	private VDiskAuthSession session;
	private AccessToken mVDiskAccessToken;
	private ProgressDialog dialog;

	// 寰崥鎺堟潈璁よ瘉鐩稿叧
	// Weibo authorization-related
	private WeiboAuth mWeiboAuth;
	private Oauth2AccessToken mWeiboAccessToken;
	private SsoHandler mSsoHandler;

	private static final int SUCCESS = 0;
	private static final int FAILED = -1;
	private Handler handler = new Handler() {

		public void handleMessage(android.os.Message msg) {

			Bundle values = msg.getData();
			switch (msg.what) {
			case SUCCESS: {
				String error = values.getString("error");
				String error_code = values.getString("error_code");

				if (error == null && error_code == null) {
					OAuthActivity.this.onComplete(values);
					session.updateOAuth2Preference(OAuthActivity.this,
							mVDiskAccessToken);
				} else if (error.equals("access_denied")) {
					// 鐢ㄦ埛鎴栨巿鏉冩湇鍔″櫒鎷掔粷鎺堜簣鏁版嵁璁块棶鏉冮檺
					// User or authorization server refuses to grant data access
					// permission
					OAuthActivity.this.onCancel();
				} else {
					OAuthActivity.this
							.onVDiskException(new VDiskServerException(error,
									Integer.parseInt(error_code)));
				}
				dialog.dismiss();
			}
				break;
			case FAILED: {
				VDiskException e = (VDiskException) values
						.getSerializable("error");
				OAuthActivity.this.onVDiskException(e);
				dialog.dismiss();
			}
				break;
			default:
				break;
			}

		};
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		/**
		 * 鍒濆鍖�Init
		 */
		AppKeyPair appKeyPair = new AppKeyPair(CONSUMER_KEY, CONSUMER_SECRET);
		/**
		 * @AccessType.APP_FOLDER - sandbox 妯″紡
		 * @AccessType.VDISK - basic 妯″紡
		 */
		session = VDiskAuthSession.getInstance(this, appKeyPair,
				AccessType.APP_FOLDER);

		setContentView(R.layout.auth_main);
		initViews();

		// 鍒涘缓寰崥璁よ瘉瀹炰緥锛岃鍙栧井鍗歍oken
		// Create a instace of Weibo authorization, and read Weibo Token.
		mWeiboAuth = new WeiboAuth(this, Constants.APP_KEY,
				Constants.REDIRECT_URL, Constants.SCOPE);
		mWeiboAccessToken = AccessTokenKeeper.readAccessToken(this);
		if (mWeiboAccessToken.isSessionValid()) {
			WeiboAccessToken weiboToken = new WeiboAccessToken();
			weiboToken.mAccessToken = mWeiboAccessToken.getToken();
			session.enabledAndSetWeiboAccessToken(weiboToken);
		}

		mVDiskAccessToken = VDiskAuthSession.getOAuth2Preference(this,
				appKeyPair);
		// 濡傛灉宸茬粡鐧诲綍锛岀洿鎺ヨ烦杞埌娴嬭瘯椤甸潰
		// If you are already logged in, jump to the test page directly.
		if (session.isLinked()) {
			startActivity(new Intent(this, VDiskTestActivity.class));
			finish();
		} else if (!TextUtils.isEmpty(mVDiskAccessToken.mRefreshToken)
				&& !VDiskAuthSession.isSessionValid(mVDiskAccessToken)) {
			// 濡傛灉寰洏AccessToken杩囨湡锛屼娇鐢≧efreshToken鍒锋柊AccessToken骞剁櫥褰�
			// If VDisk AccessToken has expired ,use RefreshToken to refresh the
			// VDisk AccessToken then login.
			dialog = new ProgressDialog(this);
			dialog.setMessage(getString(R.string.wait_for_load));

			refreshLogin();
		}
	}

	private void initViews() {

		cbUseWeiboToken = (CheckBox) this.findViewById(R.id.cb_use_weibo_token);

		btn_oauth = (Button) this.findViewById(R.id.btnOAuth);
		btn_oauth.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {

				if (cbUseWeiboToken.isChecked()) {

					if (mWeiboAccessToken.isSessionValid()) {
						weiboFastLogin();
					} else {
						ssoAuthorize();
					}

				} else {
					// 浣跨敤寰洏Token璁よ瘉锛岄渶璁剧疆閲嶅畾鍚戠綉鍧�
					// Need to set REDIRECT_URL if you want to use VDisk token.
					session.setRedirectUrl(REDIRECT_URL);
					session.authorize(OAuthActivity.this, OAuthActivity.this);
				}

			}
		});
	}

	/**
	 * 
	 * 浣跨敤RefreshToken鍒锋柊AccessToken骞剁櫥褰�
	 * 
	 * Use RefreshToken to refresh the VDisk AccessToken then login.
	 * 
	 */
	private void refreshLogin() {
		dialog.show();
		new Thread() {

			public void run() {
				Message msg = new Message();
				try {
					mVDiskAccessToken = session
							.refreshOAuth2AccessToken(
									mVDiskAccessToken.mRefreshToken,
									OAuthActivity.this);
				} catch (VDiskException e) {
					msg.what = FAILED;
					Bundle error = new Bundle();
					error.putSerializable("error", e);
					msg.setData(error);
					handler.sendMessage(msg);
					return;
				}
				Bundle values = new Bundle();
				values.putSerializable(VDiskAuthSession.OAUTH2_TOKEN,
						mVDiskAccessToken);
				msg.setData(values);
				msg.what = SUCCESS;
				handler.sendMessage(msg);
			};
		}.start();
	}

	/**
	 * 璁よ瘉缁撴潫鍚庣殑鍥炶皟鏂规硶
	 * 
	 * Callback method after authentication.
	 */
	@Override
	public void onComplete(Bundle values) {

		if (values != null) {
			AccessToken mToken = (AccessToken) values
					.getSerializable(VDiskAuthSession.OAUTH2_TOKEN);
			session.finishAuthorize(mToken);
		}

		startActivity(new Intent(this, VDiskTestActivity.class));
		finish();
	}

	/**
	 * 璁よ瘉鍑洪敊鐨勫洖璋冩柟娉�
	 * 
	 * Callback method for authentication errors.
	 */
	@Override
	public void onError(VDiskDialogError error) {
		Toast.makeText(getApplicationContext(),
				"Auth error : " + error.getMessage(), Toast.LENGTH_LONG).show();
	}

	/**
	 * 璁よ瘉寮傚父鐨勫洖璋冩柟娉�
	 * 
	 * Callback method for authentication exceptions.
	 */
	@Override
	public void onVDiskException(VDiskException exception) {
		Toast.makeText(getApplicationContext(),
				"Auth exception : " + exception.getMessage(), Toast.LENGTH_LONG)
				.show();
	}

	/**
	 * 璁よ瘉琚彇娑堢殑鍥炶皟鏂规硶
	 * 
	 * Callback method as authentication is canceled.
	 */
	@Override
	public void onCancel() {
		Toast.makeText(getApplicationContext(), "Auth cancel",
				Toast.LENGTH_LONG).show();
	}

	/**
	 * 浣跨敤寰崥 Token 鐧诲綍寰洏
	 * 
	 * Use Weibo token for authentication
	 */
	private void weiboFastLogin() {

		WeiboAccessToken weiboToken = new WeiboAccessToken();

		OAuthActivity.WEIBO_ACCESS_TOKEN = mWeiboAccessToken.getToken();
		weiboToken.mAccessToken = OAuthActivity.WEIBO_ACCESS_TOKEN;

		// 寮�惎浣跨敤寰崥Token鐨勫紑鍏�濡傛灉瑕佷娇鐢ㄥ井鍗歍oken鐨勮瘽锛屽繀椤绘墽琛屾鏂规硶
		// Run this method if you want to use Weibo token.
		session.enabledAndSetWeiboAccessToken(weiboToken);

		session.authorize(OAuthActivity.this, OAuthActivity.this);
	}

	/**
	 * 杩涜寰崥 SSO 璁よ瘉
	 * 
	 * Doing Weibo SSO authentication
	 */
	private void ssoAuthorize() {
		mSsoHandler = new SsoHandler(OAuthActivity.this, mWeiboAuth);
		mSsoHandler.authorize(new AuthListener());
	}

	/**
	 * 褰�SSO 鎺堟潈 Activity 閫�嚭鏃讹紝璇ュ嚱鏁拌璋冪敤銆�
	 * 
	 * When the SSO authorized Activity exits, this function will be invoked.
	 * 
	 * @see {@link Activity#onActivityResult}
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		// SSO 鎺堟潈鍥炶皟 Authorization callback
		// 閲嶈锛氬彂璧�SSO 鐧婚檰鐨�Activity 蹇呴』閲嶅啓 onActivityResult
		// Important: onActivityResult() must be override in Activity if you
		// want to start Weibo SSO authorization.
		//
		if (mSsoHandler != null) {
			mSsoHandler.authorizeCallBack(requestCode, resultCode, data);
		}
	}

	/**
	 * 寰崥璁よ瘉鎺堟潈鍥炶皟绫汇� 1. SSO 鎺堟潈鏃讹紝闇�鍦�{@link #onActivityResult} 涓皟鐢�
	 * {@link SsoHandler#authorizeCallBack} 鍚庯紝 璇ュ洖璋冩墠浼氳鎵ц銆�2. 闈�SSO
	 * 鎺堟潈鏃讹紝褰撴巿鏉冪粨鏉熷悗锛岃鍥炶皟灏变細琚墽琛屻� 褰撴巿鏉冩垚鍔熷悗锛岃淇濆瓨璇�access_token銆乪xpires_in銆乽id 绛変俊鎭埌
	 * SharedPreferences 涓�
	 * 
	 * After Weibo SSO authorization, this callback will be executed.
	 * 
	 */
	class AuthListener implements WeiboAuthListener {

		@Override
		public void onComplete(Bundle values) {
			// 浠�Bundle 涓В鏋�Token
			mWeiboAccessToken = Oauth2AccessToken.parseAccessToken(values);
			if (mWeiboAccessToken.isSessionValid()) {

				// 淇濆瓨 Token 鍒�SharedPreferences
				// Save the Token to the SharedPreferences
				AccessTokenKeeper.writeAccessToken(OAuthActivity.this,
						mWeiboAccessToken);
				Toast.makeText(OAuthActivity.this,
						R.string.weibosdk_demo_toast_auth_success,
						Toast.LENGTH_SHORT).show();

				weiboFastLogin();
			} else {
				// 褰撴偍娉ㄥ唽鐨勫簲鐢ㄧ▼搴忕鍚嶄笉姝ｇ‘鏃讹紝灏变細鏀跺埌 Code锛岃纭繚绛惧悕姝ｇ‘
				// When your register application signature is not correct, will
				// receive the Code, please make sure the signature is correct.
				String code = values.getString("code");
				String message = getString(R.string.weibosdk_demo_toast_auth_failed);
				if (!TextUtils.isEmpty(code)) {
					message = message + "\nObtained the code: " + code;
				}
				Toast.makeText(OAuthActivity.this, message, Toast.LENGTH_LONG)
						.show();
			}
		}

		@Override
		public void onCancel() {
			Toast.makeText(OAuthActivity.this,
					R.string.weibosdk_demo_toast_auth_canceled,
					Toast.LENGTH_LONG).show();
		}

		@Override
		public void onWeiboException(WeiboException e) {
			Toast.makeText(OAuthActivity.this,
					"Auth exception : " + e.getMessage(), Toast.LENGTH_LONG)
					.show();
		}
	}

}
