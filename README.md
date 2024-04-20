

#### **把视频转化为字符视频**

1. 把视频分割成图片
2. 把图片转成灰度图
3. 根据灰度图画字符图片，颜色深的地方用笔画多的字符，颜色浅的地方用笔画少的字符
4. 把字符图片合成视频

```java
VideoToChar videoToChar = new VideoToChar();
videoToChar.exec(
    "C:/Users/22609/Downloads/aa.mp4", //原始视频地址
    "C:/Users/22609/Downloads",  //输出文件夹
    "output.mp4",  //输出视频名称
    50,  //一行几个字符
    20);  //字符字体大小
```

