package app.openconnect;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import org.infradead.libopenconnect.LibOpenConnect;

import app.openconnect.core.UserDialog;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

public class AuthFormHandler extends UserDialog
		implements DialogInterface.OnClickListener, DialogInterface.OnDismissListener {

	public static final String TAG = "OpenConnect";

	private LibOpenConnect.AuthForm mForm;
	private Context mContext;
	private boolean isOK;
	private AlertDialog mAlert;

	private CheckBox savePassword = null;
	private boolean noSave = false;
	private String formPfx;
	private int batchMode = BATCH_MODE_DISABLED;
	private boolean mAuthgroupSet;

	private static final int BATCH_MODE_DISABLED = 0;
	private static final int BATCH_MODE_EMPTY_ONLY = 1;
	private static final int BATCH_MODE_ENABLED = 2;

	public AuthFormHandler(SharedPreferences prefs, LibOpenConnect.AuthForm form, boolean authgroupSet) {
		super(prefs);

		mForm = form;
		mAuthgroupSet = authgroupSet;
		formPfx = getFormPrefix(mForm);
		noSave = getBooleanPref("disable_username_caching");

		String s = getStringPref("batch_mode");
		if (s.equals("empty_only")) {
			batchMode = BATCH_MODE_EMPTY_ONLY;
		} else if (s.equals("enabled")) {
			batchMode = BATCH_MODE_ENABLED;
		}
	}

	@Override
	public void onDismiss(DialogInterface dialog) {
		// catches OK, Cancel, and Back button presses
		if (isOK) {
			saveAndStore();
		}
		finish(isOK ? LibOpenConnect.OC_FORM_RESULT_OK : LibOpenConnect.OC_FORM_RESULT_ERR);
	}

	@Override
	public void onClick(DialogInterface dialog, int which) {
		if (which == DialogInterface.BUTTON_POSITIVE) {
			isOK = true;
		}
	}

	private String digest(String s) {
		String out = "";
		if (s == null) {
			s = "";
		}
		try {
			MessageDigest digest = MessageDigest.getInstance("MD5");
			StringBuilder sb = new StringBuilder();
			byte d[] = digest.digest(s.getBytes("UTF-8"));
			for (byte dd : d) {
				sb.append(String.format("%02x", dd));
			}
			out = sb.toString();
		} catch (Exception e) {
			Log.e(TAG, "MessageDigest failed", e);
		}
		return out;
	}

	private String getOptDigest(LibOpenConnect.FormOpt opt) {
		StringBuilder in = new StringBuilder();

		switch (opt.type) {
		case LibOpenConnect.OC_FORM_OPT_SELECT:
			for (LibOpenConnect.FormChoice ch : opt.choices) {
				in.append(digest(ch.name));
				in.append(digest(ch.label));
			}
			/* falls through */
		case LibOpenConnect.OC_FORM_OPT_TEXT:
		case LibOpenConnect.OC_FORM_OPT_PASSWORD:
			in.append(":" + Integer.toString(opt.type) + ":");
			in.append(digest(opt.name));
			in.append(digest(opt.label));
		}
		return digest(in.toString());
	}

	private String getFormPrefix(LibOpenConnect.AuthForm form) {
		StringBuilder in = new StringBuilder();

		for (LibOpenConnect.FormOpt opt : form.opts) {
			in.append(getOptDigest(opt));
		}
		return "FORMDATA-" + digest(in.toString()) + "-";
	}

	private void fixPadding(View v) {
		v.setPadding(20, 20, 20, 20);
	}

	private LinearLayout.LayoutParams fillWidth =
			new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);

	private LinearLayout newHorizLayout(String label) {
		LinearLayout ll = new LinearLayout(mContext);
		ll.setOrientation(LinearLayout.HORIZONTAL);
		ll.setLayoutParams(fillWidth);
		fixPadding(ll);

		TextView tv = new TextView(mContext);
		tv.setText(label);
		ll.addView(tv);

		return ll;
	}

	private LinearLayout newTextBlank(LibOpenConnect.FormOpt opt, String defval) {
		LinearLayout ll = newHorizLayout(opt.label);

		TextView tv = new EditText(mContext);
		tv.setLayoutParams(fillWidth);
		if (defval != null) {
			tv.setText(defval);
		}
		if (opt.type == LibOpenConnect.OC_FORM_OPT_PASSWORD) {
			tv.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
			tv.setTransformationMethod(PasswordTransformationMethod.getInstance());
		} else {
			tv.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
		}

		opt.userData = tv;
		ll.addView(tv);
		return ll;
	}

	private void spinnerSelect(LibOpenConnect.FormOpt opt, int index) {
		LibOpenConnect.FormChoice fc = opt.choices.get((int)index);
		String s = fc.name != null ? fc.name : "";

		if (opt.userData == null) {
			// first run
			opt.userData = s;
		} else if (!s.equals(opt.userData)) {
			opt.setValue(s);
			mAlert.dismiss();
			finish(LibOpenConnect.OC_FORM_RESULT_NEWGROUP);
		}
	}

	private LinearLayout newDropdown(final LibOpenConnect.FormOpt opt, int selection) {
		List<String> choiceList = new ArrayList<String>();

	    ArrayAdapter<String> adapter = new ArrayAdapter<String>(mContext,
	    		android.R.layout.simple_spinner_item, choiceList);
	    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

	    for (LibOpenConnect.FormChoice fc : opt.choices) {
	    	choiceList.add(fc.label);
	    };

		Spinner sp = new Spinner(mContext);
		sp.setAdapter(adapter);
		sp.setLayoutParams(fillWidth);

		sp.setSelection(selection);
		spinnerSelect(opt, selection);

		sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view,
					int position, long id) {
				spinnerSelect(opt, (int)id);
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {
			}
		});

		LinearLayout ll = newHorizLayout(opt.label);
		ll.addView(sp);

		return ll;
	}

	private CheckBox newSavePasswordView(boolean isChecked) {
		CheckBox cb = new CheckBox(mContext);
		cb.setText(R.string.save_password);
		cb.setChecked(isChecked);
		fixPadding(cb);
		return cb;
	}

	private void saveAndStore() {
		for (LibOpenConnect.FormOpt opt : mForm.opts) {
			switch (opt.type) {
			case LibOpenConnect.OC_FORM_OPT_TEXT: {
				TextView tv = (TextView)opt.userData;
				String s = tv.getText().toString();
				if (!noSave) {
					setStringPref(formPfx + getOptDigest(opt), s);
				}
				opt.setValue(s);
				break;
			}
			case LibOpenConnect.OC_FORM_OPT_PASSWORD: {
				TextView tv = (TextView)opt.userData;
				String s = tv.getText().toString();
				if (savePassword != null) {
					boolean checked = savePassword.isChecked();
					setStringPref(formPfx + getOptDigest(opt), checked ? s : "");
					setStringPref(formPfx + "savePass", checked ? "true" : "false");
				}
				opt.setValue(s);
				break;
			}
			case LibOpenConnect.OC_FORM_OPT_SELECT:
				String s = (String)opt.userData;
				if (!noSave) {
					setStringPref(formPfx + getOptDigest(opt), s);
					if ("group_list".equals(opt.name)) {
						setStringPref("authgroup", s);
					}
				}
				opt.setValue(s);
				break;
			}
		}
	}

	// If the user had saved a preferred authgroup, submit a NEWGROUP request before rendering the form
	public Object earlyReturn() {
		String authgroup = getStringPref("authgroup");

		if (mAuthgroupSet || authgroup.equals("")) {
			return null;
		}

		for (LibOpenConnect.FormOpt opt : mForm.opts) {
			if (opt.name.equals(mForm.authgroupField)) {
				LibOpenConnect.FormChoice selected = opt.choices.get(mForm.authgroupSelection);
				if (authgroup.equals(selected.name)) {
					// already good to go
					return null;
				} else {
					// not selected, so send a request back to libopenconnect
					opt.setValue(selected.name);
					return LibOpenConnect.OC_FORM_RESULT_NEWGROUP;
				}
			}
		}
		return null;
	}

	public void onStart(Context context) {
		final AuthFormHandler h = this;

		super.onStart(context);
		mContext = context;
		isOK = false;
		LinearLayout v = new LinearLayout(mContext);
		v.setOrientation(LinearLayout.VERTICAL);

		boolean hasPassword = false, allFilled = true, hasUserOptions = false;
		String defval;

		for (LibOpenConnect.FormOpt opt : mForm.opts) {
			switch (opt.type) {
			case LibOpenConnect.OC_FORM_OPT_PASSWORD:
				hasPassword = true;
				/* falls through */
			case LibOpenConnect.OC_FORM_OPT_TEXT:
				defval = noSave ? "" : getStringPref(formPfx + getOptDigest(opt));
				if (defval.equals("")) {
					allFilled = false;
				}
				v.addView(newTextBlank(opt, defval));
				hasUserOptions = true;
				break;
			case LibOpenConnect.OC_FORM_OPT_SELECT:
				if (opt.choices.size() == 0) {
					break;
				}

				int selection = 0;
				if (opt.name.equals(mForm.authgroupField)) {
					selection = mForm.authgroupSelection;
				} else {
					// do any servers actually use non-authgroup downdowns?
					defval = noSave ? "" : getStringPref(formPfx + getOptDigest(opt));
					for (int i = 0; i < opt.choices.size(); i++) {
						if (opt.choices.get(i).name.equals(defval)) {
							selection = i;
						}
					}
				}
				v.addView(newDropdown(opt, selection));
				hasUserOptions = true;
				break;
			}
		}
		if (hasPassword && !noSave) {
			boolean savePass = !getStringPref(formPfx + "savePass").equals("false");
			savePassword = newSavePasswordView(savePass);
			v.addView(savePassword);
		}

		if ((batchMode == BATCH_MODE_EMPTY_ONLY && allFilled) ||
			batchMode == BATCH_MODE_ENABLED || !hasUserOptions) {
			saveAndStore();
			finish(LibOpenConnect.OC_FORM_RESULT_OK);
			return;
		}

		mAlert = new AlertDialog.Builder(mContext)
				.setView(v)
				.setTitle(mContext.getString(R.string.login_title, getStringPref("profile_name")))
				.setPositiveButton(R.string.ok, h)
				.setNegativeButton(R.string.cancel, h)
				.create();
		mAlert.setOnDismissListener(h);
		mAlert.show();
	}

	public void onStop(Context context) {
		super.onStop(context);
		if (mAlert != null) {
			mAlert.dismiss();
			mAlert = null;
		}
	}
}