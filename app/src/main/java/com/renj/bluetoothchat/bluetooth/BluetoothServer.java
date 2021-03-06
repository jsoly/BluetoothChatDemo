package com.renj.bluetoothchat.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.Toast;

import com.renj.bluetoothchat.common.Logger;

import java.io.IOException;

/**
 * ======================================================================
 * <p>
 * 作者：Renj
 * <p>
 * 创建时间：2017-09-21   10:57
 * <p>
 * 描述：蓝牙服务端控制类(服务器和客户端一对一)
 * <p>
 * 修订历史：
 * <p>
 * ======================================================================
 */
public class BluetoothServer {
    private Context mContext;
    // 连接类型 安全/受保护的连接(Secure)或者不安全的连接/不受保护的连接(Insecure)
    private String mSocketType;
    // 定义蓝牙适配器
    private BluetoothAdapter mBluetoothAdapter;
    // 蓝牙服务器Socket对象
    private BluetoothServerSocket mBluetoothServerSocket;
    // 客户端连接监听(当客户端成功连接时回调)
    private ServerAcceptListener mServerAcceptListener;
    // 蓝牙服务端状态监听 启动(成功、失败)/关闭
    private ServerStateListener mServerStateListener;
    // 服务器端线程对象
    private BluetoothServerThread mBluetoothServerThread;
    // 使用单例
    private static BluetoothServer mBluetoothServer;

    private final int MSG_OPEN_SUCCEED = 0XFF01;
    private final int MSG_OPEN_FAILED = 0XFF02;
    private final int MSG_CLOSE = 0XFF03;
    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_OPEN_SUCCEED:
                    boolean secure = (boolean) msg.obj;
                    if (mServerStateListener != null)
                        mServerStateListener.onOpenSucceed(secure);
                    break;
                case MSG_OPEN_FAILED:
                    Exception e = (Exception) msg.obj;
                    if (mServerStateListener != null)
                        mServerStateListener.onOpenFailed(e);
                    break;
                case MSG_CLOSE:
                    if (mServerStateListener != null)
                        mServerStateListener.onClose();
                    break;
            }
        }
    };

    private BluetoothServer(Context context) {
        this.mContext = context;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    /**
     * 获取BluetoothServer对象
     *
     * @return
     */
    public static BluetoothServer newInstance(Context context) {
        if (mBluetoothServer == null) {
            synchronized (BluetoothServer.class) {
                if (mBluetoothServer == null) {
                    mBluetoothServer = new BluetoothServer(context);
                }
            }
        }
        return mBluetoothServer;
    }

    /**
     * 设备是否支持蓝牙
     *
     * @return true：支持 false：不支持
     */
    public boolean hasBluetooth() {
        if (mBluetoothAdapter == null) {
            Toast.makeText(mContext, "该设备不支持蓝牙", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    /**
     * 打开设备蓝牙
     *
     * @return
     */
    public BluetoothServer openBluetooth() {
        if(!hasBluetooth()){
            mServerStateListener.onOpenFailed(new Exception("设备不支持蓝牙"));
            return mBluetoothServer;
        }
        if (!mBluetoothAdapter.isEnabled()) {
            boolean enable = mBluetoothAdapter.enable();
            if(enable)
                Toast.makeText(mContext, "蓝牙打开成功", Toast.LENGTH_SHORT).show();
            else
                Toast.makeText(mContext, "蓝牙打开失败", Toast.LENGTH_SHORT).show();
            // Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            // mContext.startActivity(intent);
        }else{
            Toast.makeText(mContext, "蓝牙已打开", Toast.LENGTH_SHORT).show();
        }
        return mBluetoothServer;
    }

    /**
     * 设置蓝牙服务端状态监听 启动(成功、失败)/关闭
     *
     * @param serverStateListener
     */
    public BluetoothServer setOnServerStateListener(ServerStateListener serverStateListener) {
        this.mServerStateListener = serverStateListener;
        return mBluetoothServer;
    }

    /**
     * 打开蓝牙服务器端
     *
     * @param secure               是否安全打开
     * @param serverAcceptListener 客户端连接监听器
     */
    public BluetoothServer openBluetoothServer(boolean secure, ServerAcceptListener serverAcceptListener) {
        if(!hasBluetooth()){
            mServerStateListener.onOpenFailed(new Exception("设备不支持蓝牙"));
            return mBluetoothServer;
        }
        // 判断蓝牙是否已打开
        if (!mBluetoothAdapter.isEnabled()) {
            Toast.makeText(mContext, "请先打开蓝牙", Toast.LENGTH_SHORT).show();
            return mBluetoothServer;
        }

        if (mBluetoothServerThread == null) {
            this.mServerAcceptListener = serverAcceptListener;
            this.mBluetoothServerThread = new BluetoothServerThread(secure);
            this.mBluetoothServerThread.start();
        } else {
            Logger.d("BluetoothServer already open ...");
            Toast.makeText(mContext, "服务器已启动", Toast.LENGTH_SHORT).show();
        }
        return mBluetoothServer;
    }

    /**
     * 关闭蓝牙服务器
     */
    public BluetoothServer closeBluetoothServer() {
        if(!hasBluetooth()){
            mServerStateListener.onOpenFailed(new Exception("设备不支持蓝牙"));
            return mBluetoothServer;
        }
        if (mBluetoothServerThread != null) {
            mBluetoothServerThread.interrupt();
            mBluetoothServerThread = null;
        }
        mHandler.sendEmptyMessage(MSG_CLOSE);
        return mBluetoothServer;
    }

    /**
     * 客户端连接监听器，<b>方法运行在子线程</b>
     */
    public interface ServerAcceptListener {
        /**
         * 连接成功，<b>方法运行在子线程</b>
         *
         * @param bluetoothSocket 蓝牙Socket对象
         */
        void onAccept(BluetoothSocket bluetoothSocket);
    }

    /**
     * 蓝牙服务端打开状态监听<br />
     * 启动(成功、失败)/关闭
     */
    public abstract static class ServerStateListener {
        /**
         * 启动成功
         *
         * @param secure 连接类型 <br />
         *               true：安全/受保护的连接(Secure)<br />
         *               false：不安全的连接/不受保护的连接(Insecure)
         */
        public void onOpenSucceed(boolean secure) {
        }

        /**
         * 启动失败
         *
         * @param e 异常信息
         */
        public void onOpenFailed(Exception e) {
        }

        /**
         * 关闭
         */
        public void onClose() {
        }
    }

    /**
     * 蓝牙端服务端线程类(一对一)
     */
    class BluetoothServerThread extends Thread {
        /**
         * 构造函数
         *
         * @param secure 连接类型<br />
         *               true：安全/受保护的连接(Secure)<br />
         *               false：不安全的连接/不受保护的连接(Insecure)
         */
        public BluetoothServerThread(boolean secure) {
            mSocketType = secure ? "Secure" : "Insecure";
            BluetoothServerSocket tmp = null;
            try {
                if (secure) {
                    tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(
                            Constants.NAME_SECURE, Constants.MY_UUID_SECURE);
                } else {
                    tmp = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(
                            Constants.NAME_INSECURE, Constants.MY_UUID_INSECURE);
                }

                Message message = Message.obtain();
                message.what = MSG_OPEN_SUCCEED;
                message.obj = secure;
                mHandler.sendMessage(message);

                Logger.i("Socket Type：" + mSocketType + " listen() succeed");
            } catch (IOException e) {
                Message message = Message.obtain();
                message.what = MSG_OPEN_FAILED;
                message.obj = e;
                mHandler.sendMessage(message);

                Logger.e("Socket Type：" + mSocketType + " listen() failed\n" + e);
            }
            mBluetoothServerSocket = tmp;
        }

        @Override
        public void run() {
            BluetoothSocket bluetoothSocket = null;
            try {
                bluetoothSocket = mBluetoothServerSocket.accept();
                Logger.i("Socket Type：" + mSocketType + " accept() succeed");
            } catch (IOException e) {
                Logger.e("Socket Type: " + mSocketType + "accept() failed\n" + e);
            }

            if (bluetoothSocket != null && bluetoothSocket.isConnected()) {
                if (mServerAcceptListener != null)
                    mServerAcceptListener.onAccept(bluetoothSocket);
            }
        }
    }
}
