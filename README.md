ImageProcess 插件说明
====================

导出的类名ImageProcess
--------------------

* openCamera 方法说明 调用自定义相机并裁剪、二值处理
- ImageProcess.openCamera(savedFilePath,success,error)
    savedFilePath : 要保存的文件路径 可为空
    success       : 成功回调方法，返回string类型的文件路径
    error         : 失败回调方法，返回string类型错误提示

* openAlbum 方法说明 调用系统相册选择图片后并裁剪、二值处理(仅android)
- ImageProcess.openAlbum(savedFilePath,success,error)
    savedFilePath : 要保存的文件路径 可为空
    success       : 成功回调方法，返回string类型的文件路径
    error         : 失败回调方法，返回string类型错误提示
    
* openCrop 方法说明 对指定图片进行调用裁剪、二值处理
- ImageProcess.openCrop(savedFilePath,selectFilePath,success,error)
    savedFilePath  : 要保存的文件路径 可为空
    selectFilePath : 需裁剪、二值处理的图片路径 不可为空
    success        : 成功回调方法，返回string类型的文件路径
    error          : 失败回调方法，返回string类型错误提示