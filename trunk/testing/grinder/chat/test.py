from net.grinder.script import Test
from net.grinder.script.Grinder import grinder
from net.grinder.plugin.http import HTTPRequest
 
test1 = Test(1, "Request resource")
request1 = test1.wrap(HTTPRequest())
 
class TestRunner:
    def __call__(self):
        result = request1.GET("http://test.constellation.tv/")