#include "AppDelegate.h"
#include "platform/android/jni/JniHelper.h"
#include <jni.h>
#include <android/log.h>

#include "cocos2d.h"
#include "HelloWorldScene.h"

#define EGL_EGLEXT_PROTOTYPES

#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2ext.h>

#define  LOG_TAG    "main"
#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG,LOG_TAG,__VA_ARGS__)

using namespace cocos2d;

extern "C"
{

jint JNI_OnLoad(JavaVM *vm, void *reserved)
{
    JniHelper::setJavaVM(vm);

    return JNI_VERSION_1_4;
}

void Java_org_cocos2dx_lib_Cocos2dxRenderer_nativeInit(JNIEnv*  env, jobject thiz, jint w, jint h)
{
    if (!CCDirector::sharedDirector()->getOpenGLView())
    {
        CCEGLView *view = CCEGLView::sharedOpenGLView();
        view->setFrameSize(w, h);

        AppDelegate *pAppDelegate = new AppDelegate();
        CCApplication::sharedApplication()->run();
    }
    /*
    else
    {
        ccDrawInit();
        ccGLInvalidateStateCache();
        
        CCShaderCache::sharedShaderCache()->reloadDefaultShaders();
        CCTextureCache::reloadAllTextures();
        CCNotificationCenter::sharedNotificationCenter()->postNotification(EVNET_COME_TO_FOREGROUND, NULL);
        CCDirector::sharedDirector()->setGLDefaultValues(); 
    }
    */
}

EGLImageKHR eglImage =NULL;
EGLDisplay eglDisplay =NULL;

void Java_com_macaronics_cclw_cclwservice_nativeAttachEGLImageKHR(JNIEnv*  env, jobject thiz)
{
    if (eglImage !=NULL)
    {
        glEGLImageTargetTexture2DOES(GL_TEXTURE_2D, eglImage);
    }
}

void Java_com_macaronics_cclw_cclwservice_nativeDestroyEGLImageKHR(JNIEnv*  env, jobject thiz)
{
    if (eglImage !=NULL && eglDisplay !=NULL)
    {
        bool ret =eglDestroyImageKHR(eglDisplay, eglImage);
        LOGD("cclw, nativeDestroyEGLImageKHR ret val =%d", ret);

        eglImage =NULL;
        eglDisplay =NULL;
    }
}

void Java_com_macaronics_cclw_cclwservice_nativeRenderToTexture(JNIEnv*  env, jobject thiz)
{
    int textureID =HelloWorld::renderToTexture();

    if (eglImage ==NULL)
    {
        LOGD("cclw, nativeRenderToTexture - Generate EGLImageKHR ...");
        EGLint imageAttributes[] = {
            EGL_GL_TEXTURE_LEVEL_KHR,   0, // mip map level to reference
            EGL_IMAGE_PRESERVED_KHR,    EGL_FALSE,
            EGL_NONE,                   EGL_NONE
        };

        eglDisplay =eglGetCurrentDisplay();
        EGLContext eglContext =eglGetCurrentContext();
        LOGD("cclw, eglDisplay=%d, eglContext=%d, textureID=%d", eglDisplay, eglContext, textureID);

        eglImage =
            eglCreateImageKHR(
                eglDisplay,
                eglContext,
                EGL_GL_TEXTURE_2D_KHR,
                //EGL_GL_RENDERBUFFER_KHR,
                reinterpret_cast<EGLClientBuffer>(textureID),
                imageAttributes
            );
    }
}

}
