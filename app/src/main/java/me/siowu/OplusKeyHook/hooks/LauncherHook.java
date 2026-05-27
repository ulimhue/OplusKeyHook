package me.siowu.OplusKeyHook.hooks;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class LauncherHook {
    private static boolean sRegistered = false;

    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.android.launcher".equals(lpparam.packageName))
            return;

        Class<?> sysUiAppClass = XposedHelpers.findClass("com.android.common.LauncherApplication", lpparam.classLoader);
        XposedHelpers.findAndHookMethod(sysUiAppClass, "onCreate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (sRegistered) return;
                Application app = (Application) param.thisObject;
                registerCustomReceiver(app);
                sRegistered = true;
            }
        });
    }

    private void registerCustomReceiver(Context context) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                // 获取广播中的 cmd 参数
                String cmd = intent.getStringExtra("cmd");
                if (cmd == null || cmd.isEmpty()) {
                    XposedBridge.log("未收到 cmd 参数，忽略执行");
                    return;
                }

                XposedBridge.log("收到命令: " + cmd);

                // 在新线程中执行 shell 命令，避免阻塞广播接收器主线程
                new Thread(() -> {
                    execShell(cmd);
                }).start();
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction("me.siowu.OplusKeyHook.TRIGGER");

        // 注意：此处需要 minSdk >= 26 或使用反射，否则报错
        // 若 minSdk < 26，请改用反射方式注册
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        XposedBridge.log("自定义广播接收器已在 launcher 中成功注册");
    }

    private void execShell(String cmd) {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec("su");

            OutputStreamWriter os = new OutputStreamWriter(p.getOutputStream());
            BufferedReader is = new BufferedReader(new InputStreamReader(p.getInputStream()));
            BufferedReader es = new BufferedReader(new InputStreamReader(p.getErrorStream()));

            // 写入命令
            os.write(cmd + "\n");
            os.write("exit\n");
            os.flush();

            // 读取输出
            String line;
            StringBuilder out = new StringBuilder();
            while ((line = is.readLine()) != null) out.append(line).append("\n");

            StringBuilder err = new StringBuilder();
            while ((line = es.readLine()) != null) err.append(line).append("\n");

            int exit = p.waitFor();

            XposedBridge.log("命令执行结束 exit=" + exit);
            if (out.length() > 0) XposedBridge.log("输出:\n" + out);
            if (err.length() > 0) XposedBridge.log("错误:\n" + err);

        } catch (Exception e) {
            XposedBridge.log("Shell 执行异常: " + e);
        } finally {
            if (p != null) p.destroy();
        }
    }
}