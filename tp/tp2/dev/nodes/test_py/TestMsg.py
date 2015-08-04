from py_interface import erl_term

class TestMsg():

	def __init__(self, msg = None):
		self.resultObject = [None] * 1
		if (msg is not None):
			self.set_strParam(str(msg[0]))

	def get_strParam(self): return self._strParam


	def set_strParam(self, val):
		self._strParam = val
		self.resultObject[0] = erl_term.ErlString(val)



	def getMsg(self):
		return erl_term.ErlTuple(self.resultObject);