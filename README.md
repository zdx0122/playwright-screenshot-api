# playwright-screenshot-api
访问任意URL并截图

## 截图API
### API 1
接口api：https://127.0.0.1:9092/api/screenshot?url=http://baidu.com

修改后面的url参数即可使用截图功能

### API 2
指定参数进行截图：
```
GET http://127.0.0.1:9092/api/screenshot/take
?url=https://baidu.com
&format=.webp
&fullPage=true
&quality=100
&uploadToR2=false
```

参数描述：
```
format = .png | .jpeg | .webp #图片格式
fullPage = true | false #是否截取整个页面
quality = 0-100 #图片质量
uploadToR2 = true | false   #是否上传到R2
```