package com.daiyimo.maeve.cap.phone.a11y;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 应用名单与敏感页保护的真机测试页(Java:测试包的页面运行在测试包自己的进程里、不带 Kotlin 标准库):
 * - 「手机号」输入框:可输入、按词认不出是秘密框,不能误标成密码框;
 * - 「密码」输入框:系统标了密码(和登录页的密码框一样),要标成秘密框;
 * - 「验证码」输入框:不标密码,靠「验证码」三个字认出来;
 * - 两行文字:裸的银行卡号、身份证号(入模前要打码的那两种)。
 */
public class SensitivePageTestActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(line("账号登录"));
        EditText phone = new EditText(this);
        phone.setHint("手机号");
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        root.addView(phone);
        EditText password = new EditText(this);
        password.setHint("密码");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(password);
        EditText code = new EditText(this);
        code.setHint("验证码");
        code.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(code);
        root.addView(line("6222020200112233"));
        root.addView(line("110101199003077758"));
        setContentView(root);
    }

    private TextView line(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(24f);
        return v;
    }
}
