openCamera 方法说明 调用自定义相机并裁剪、二值处理 
ImageProcess.openCamera()
//注意 ：在调用这个拍照方法，要传入这些参数 
this.srcType = CAMERA;
this.destType = FILE_URI; 
this.saveToPhotoAlbum = false //是否保存到相册 
this.targetHeight = 600; //拍照图片的高度 
this.targetWidth = 800; //拍照图片的宽度 
this.encodingType = JPEG;
this.mediaType = PICTURE; 
this.mQuality = 50; 
this.allowEdit = true; //这个是设置是否裁剪图片，true代表默认裁剪


openAlbum 方法说明 调用系统相册选择图片后并裁剪、二值处理(仅android) 
ImageProcess.openAlbum() //注意这个方法是调用系统相册，具体情况具体调用 

openCrop 方法说明 对指定图片进行调用裁剪、二值处理 
ImageProcess.openCrop() 
