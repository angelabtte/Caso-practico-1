import sys
import os
sys.path.append(os.path.dirname(os.path.abspath("\app"))) # Añade el directorio actual

from flask import Flask, request, jsonify
from calc import Calculator

app = Flask(__name__)
calc = Calculator()

@app.route('/add', methods=['POST'])
def add():
    data = request.get_json()
    a = data['a']
    b = data['b']
    result = calc.add(a, b)
    return jsonify({'result': result})

@app.route('/subtract', methods=['POST'])
def subtract():
    data = request.get_json()
    a = data['a']
    b = data['b']
    result = calc.substract(a, b)
    return jsonify({'result': result})

@app.route('/multiply', methods=['POST'])
def multiply():
    data = request.get_json()
    a = data['a']
    b = data['b']
    result = calc.multiply(a, b)
    return jsonify({'result': result})

@app.route('/divide', methods=['POST'])
def divide():
    data = request.get_json()
    a = data['a']
    b = data['b']
    result = calc.divide(a, b)
    return jsonify({'result': result})

if __name__ == '__main__':
    app.run(debug=True)
