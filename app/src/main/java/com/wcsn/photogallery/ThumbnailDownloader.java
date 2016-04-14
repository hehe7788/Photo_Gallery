package com.wcsn.photogallery;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.util.LruCache;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by suiyue on 2016/4/7 0007.
 * ThumbnailDownloader需要使用对象来标识每一次下载
 */
public class ThumbnailDownloader<Token> extends HandlerThread {
    private static final String TAG = "ThumbnailDownloader";
    private static final int MESSAGE_DOWNLOAD = 0;
    private static final int BITMAP_CACHE = 20;
    private static final String CACHE_TOKEN = "imageView";

    Handler mHandler;
    Map<Token,String> requestMap = Collections.synchronizedMap(new HashMap<Token, String>());
    Handler mResponseHandler;
    Listener<Token> mListener;

    //mLruCache键值对是String-Bitmap
    private LruCache<String, Bitmap> mLruCache;

    public interface Listener<Token> {
        void onThumbnailDownloader(Token token, Bitmap thumbnail);
    }

    public void setListener(Listener<Token> listener) {
        mListener = listener;
    }

    /**
     * 构造函数，使用时传入主线程Handler
     * @param responseHandler 来自主线程的Handler
     */
    public ThumbnailDownloader(Handler responseHandler) {
        super(TAG);
        mResponseHandler = responseHandler;
        mLruCache = new LruCache<>(BITMAP_CACHE);
    }

    /**
     * 把Token-URL键值对放入map
     * @param token
     * @param url
     */
    public void queueThumbnail(Token token, String url) {
        Log.e(TAG, "Got an URL : " + url);
        requestMap.put(token, url);
        //以MESSAGE_DOWNLOAD为what，Token为obj从消息池中获取一条消息，
        // 并把该消息发送出去放到消息队列中
        mHandler.obtainMessage(MESSAGE_DOWNLOAD, token)
                .sendToTarget();
    }

    /**
     * 由于HandlerThread.onLooperPrepared()方法调用发送在Looper第一次检查消息队列之前
     * 该方法是创建Handler的好地方
     */
    @SuppressWarnings("HandlerLeak")
    @Override
    protected void onLooperPrepared() {
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                //检查消息，获取Token，传递给handleRequest
                if (msg.what == MESSAGE_DOWNLOAD) {
                    @SuppressWarnings("unchecked")
                    Token token = (Token) msg.obj;
                    Log.e(TAG, "Got a request for url: " + requestMap.get(token));
                    //下载
                    handleRequest(token);
                }
            }
        };
    }

    /**
     *
     * @param token
     */
    private void handleRequest(final Token token) {

        try {
            //确认url存在
            final String url = requestMap.get(token);
            if (url == null) {
                return;
            }
            //传递url给FlickrFetchr新实例
            byte[] bitmapBytes = new FlickrFetchr().getUrlBytes(url);
            //使用BitmapFactory将字节数组转换为位图
            final Bitmap bitmap = BitmapFactory.decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
            Log.e(TAG, "Bitmap create");

            mResponseHandler.post(new Runnable() {
                @Override
                public void run() {
                    if(requestMap.get(token) != url) {
                        return;
                    }
                    requestMap.remove(token);
                    mListener.onThumbnailDownloader(token, bitmap);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 如果用户选择屏幕，因imageView视图失效，
     */
    public void clearQueue() {
        mHandler.removeMessages(MESSAGE_DOWNLOAD);
        requestMap.clear();
    }
}
