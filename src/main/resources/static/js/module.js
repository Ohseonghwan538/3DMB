function mouseOverInterface(_isOver) {
    if (typeof Module == "object") {
        Module.XDIsMouseOverDiv(_isOver);
    }
}
window.onresize = function(e) {
    if (typeof Module == 'object') {
        if (typeof Module.Resize == 'function') {
            Module.Resize(window.innerWidth, window.innerHeight);
            Module.XDRenderData();
        }
    }
};
var Module = {

    locateFile : function(s) {
        return "/js/engine/"+ s;
    },
    postRun: function() {

        // 엔진 초기화 API 호출(필수)
        Module.initialize({
            container: document.getElementById("map"),
            terrain : {
                dem : {
                    url : "https://xdworld.vworld.kr",
                    name : "dem",
                    servername : "XDServer3d",
                    encoding : true
                },
                image : {
                    url : "https://xdworld.vworld.kr",
                    name : "tile",
                    servername : "XDServer3d"
                },
            },
            worker : {
                use : true,
                path : "/js/worker/XDWorldWorker.js",
                count : 5
            },
            defaultKey : "eza2eBBqd!Hmd!JQ45QpEpB~#Fb!EQBmeFDP4FEPDzb1dFg21I=="
        });

        // 엔진 초기화 완료 후 전역 이벤트로 신호 발신 (main.js가 받을 수 있도록)
        window.XDWorldReady = true;
        window.dispatchEvent(new CustomEvent("XDWorldLoaded"));
    }
};
