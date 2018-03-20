var exec = require('cordova/exec');
var ImageProcess = {
    openCamera: function (savedFilePath, success, error) {
        exec(success, error, "ImageProcess", "openCamera", [savedFilePath]);
    },
    openAlbum: function (savedFilePath, success, error){
        exec(success, error, "ImageProcess", "openAlbum", [savedFilePath]);
    },
    openCrop: function (savedFilePath,selectedFilePath, success, error){
        exec(success, error, "ImageProcess", "openCrop", [savedFilePath,selectedFilePath])
    }
};

module.exports = ImageProcess;
