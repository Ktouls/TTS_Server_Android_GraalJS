const DirectUploadJS = {
    "橘途网盘 (永久有效)": function(config) {
        const resp = upload('http://v2.jt12.de/up-v2.php', config)
        const str = resp.body().string()
        const result = JSON.parse(str)
        if (result['code'] !== 0) {
            throw "error: " + result['msg']
        }

        return result['msg']
    },

    "喵公子 (有效期2天)": function(config) {
        const url = 'https://sy.mgz6.com/shuyuan'
        const resp = upload(url, config)
        const result = JSON.parse(resp.body().string())
        if (result['msg'] !== 'success') {
            throw "error: " + result['msg']
        }

        return url + '/' + result['data']
    },

    "Catbox (有效期未知)": function(config) {
        const form = {
            'fileToUpload': {
                'body': config,
                'fileName': "config.json",
                'contentType': "application/json"
            },
            'reqtype': 'fileupload',
        }
        const resp = ttsrv.httpPostMultipart('https://catbox.moe/user/api.php', form)
        if (resp.code() !== 200) {
            throw 'error: HTTP-' + resp.code()
        }

        return resp.body().string()
    }
}

function upload(url, config, extra) {
    const form = {
        "file":{
            'body': config,
            'fileName': 'config.json',
            'contentType': 'application/json',
          }
    }

    return ttsrv.httpPostMultipart(url, form)
}
