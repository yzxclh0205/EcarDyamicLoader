/*
**        DroidPlugin Project
**
** Copyright(c) 2015 Andy Zhang <zhangyong232@gmail.com>
**
** This file is part of DroidPlugin.
**
** DroidPlugin is free software: you can redistribute it and/or
** modify it under the terms of the GNU Lesser General Public
** License as published by the Free Software Foundation, either
** version 3 of the License, or (at your option) any later version.
**
** DroidPlugin is distributed in the hope that it will be useful,
** but WITHOUT ANY WARRANTY; without even the implied warranty of
** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
** Lesser General Public License for more details.
**
** You should have received a copy of the GNU Lesser General Public
** License along with DroidPlugin.  If not, see <http://www.gnu.org/licenses/lgpl.txt>
**
**/

package com.ecar.epark.edroidloaer.manager;

import android.app.Application;
import android.text.TextUtils;


import com.ecar.epark.edroidloaer.core.PluginDirHelper;
import com.ecar.epark.edroidloaer.db.DroidSpManager;
import com.ecar.epark.edroidloaer.down.DownJarManager;
import com.ecar.epark.edroidloaer.interfaces.IPluginLoader;
import com.ecar.epark.edroidloaer.util.DLFileUtils;

import java.io.File;
import dalvik.system.DexClassLoader;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * 此服务模仿系统的PackageManagerService，提供对插件简单的管理服务。
 * Created by Andy Zhang(zhangyong232@gmail.com) on 2015/2/12.
 */
public class PluginManager implements IPluginLoader {

    private Application mContext;
    private DroidSpManager spManager;
    public DexClassLoader dexClassLoader;

    public static final String DEX_TEMP_CACHE_PATH_ENDING = "temp";

    public PluginManager(Application context) {
        mContext = context;
        spManager = new DroidSpManager(context);
    }

    /**
     * 加载jar（dex）
     * @param jarName    jar名称，当做jar包目录名
     * @param jarVersion jar 版本，当做jar 文件名
     * @param downUrl    若没有对应版本则下载后加载
     * @return 是否加载成功
     */
    @Override
    public boolean initLoaderJar(String jarName, String jarVersion, String downUrl) {
        boolean result = false;
        //1.查sp jar版本 与当前版本比较。不同则更新 通则加载。
        if (TextUtils.isEmpty(jarVersion) || TextUtils.isEmpty(jarName)) {
            return false;
        }
        String jarVersionCache = spManager.getJarVersionByName(jarName);
        //对比缓存
        if (!jarVersion.equals(jarVersionCache)) {
            //不相同：去下载
            result = true;//downJar(jarName, jarVersion, downUrl);
            if(!result){
                return false;
            }
            spManager.setJarVersionByName(jarName,jarVersion);
        }
        //1.删除其他同类文件
        String cacheDir = PluginDirHelper.getPluginDalvikCacheDir(mContext, jarName);//jarName 当文件目录名，jarVersion 当文件名
        DLFileUtils.deleteFileButOne(new File(cacheDir),jarVersion);
        //2.加载
        loaderDex(jarName,jarVersion);//传递文件目录名 进去
        return true;
    }

    private void loaderDex(String folderName,String fileName) {
        String dexOutputDir = PluginDirHelper.getPluginDalvikCacheDir(mContext, folderName);
        String dexPath = PluginDirHelper.getPluginDalvikCacheDexFile(mContext, folderName.concat(DEX_TEMP_CACHE_PATH_ENDING), fileName);
        dexClassLoader = new DexClassLoader(dexPath, dexOutputDir, null, this
                .getClass().getClassLoader());
    }

    /**
     * 下载jar包
     *
     * @param folderName jarName 目录名称
     * @param fileName   jarVersion 文件名称
     * @param downUrl
     */
    private boolean downJar(String folderName, String fileName, final String downUrl) {
        final String downPath = PluginDirHelper.getPluginDalvikCacheDexFile(mContext, folderName, fileName);
        Observable<Boolean> treeMapObservable = Observable.create(new Observable.OnSubscribe<Boolean>() {
            @Override
            public void call(Subscriber<? super Boolean> subscriber) {
                DownJarManager downJarManager = new DownJarManager(downPath, downUrl);
                boolean downResult = downJarManager.startDownFile();
                subscriber.onNext(downResult);
            }
        }).compose(this.<Boolean>rxScheduler());
        Boolean downResult = treeMapObservable.toBlocking().first();
        return downResult;
    }

    /**
     * 线程转换
     * @param <T>
     * @return
     */
    private  <T> Observable.Transformer<T, T> rxScheduler() {
        return new Observable.Transformer<T, T>() {
            public Observable<T> call(Observable<T> tObservable) {
                return tObservable.subscribeOn(Schedulers.io()).unsubscribeOn(AndroidSchedulers.mainThread()).observeOn(AndroidSchedulers.mainThread());
            }
        };
    }
}
