pipeline {
    agent any

    stages {
        stage('Get Code') {
            steps {
                git 'https://github.com/angelabtte/Caso-practico-1'
                bat 'dir /s /b'
                echo "${env.WORKSPACE}"
            }
        }

        stage('Unit') {
            steps {
                bat """
                    set PYTHONPATH=%WORKSPACE%
                    C:\\Python\\python.exe -m coverage run --branch -m pytest test\\unit
                    C:\\Python\\python.exe -m coverage xml
                    C:\\Python\\python.exe -m coverage report --fail-under=0 --skip-covered > coverage.txt
                    C:\\Python\\python.exe -m coverage json -o coverage.json
                """
            }
        }

        stage('Rest') {
            steps {
                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                    bat """
                        set FLASK_APP=app\\api.py
                        start /B cmd /c "C:\\Python\\python.exe -m flask run --port=5000"
                        ping -n 10 127.0.0.1 >nul
                        cd /d "C:\\Users\\USER\\Desktop\\Unir - Angela\\helloworld-master-Proyecto1\\test"
                        start /B cmd /c "java -jar wiremock-standalone-3.13.0.jar --port 9090"
                        ping -n 10 127.0.0.1 >nul
                        cd /d "%WORKSPACE%"
                        C:\\Python\\python.exe -m pytest --junitxml=rest-report.xml test\\rest
                    """
                }
                junit 'rest-report.xml'
            }
        }

        stage('Coverage') {
            steps {
                catchError(buildResult: 'SUCCESS', stageResult: 'SUCCESS') {
                    recordCoverage(
                        tools: [[parser: 'COBERTURA', pattern: 'coverage.xml']],
                        qualityGates: [
                            [threshold: 95.0, metric: 'LINE', integerThreshold: 95],
                            [threshold: 85.0, metric: 'LINE', integerThreshold: 85, criticality: 'ERROR'],
                            [threshold: 90.0, metric: 'BRANCH', integerThreshold: 90],
                            [threshold: 80.0, metric: 'BRANCH', integerThreshold: 80, criticality: 'ERROR']
                        ]
                    )
                }
            }
        }

        stage('Static') {
            steps {
                bat '''
                    C:\\Python\\python.exe -m flake8 --exit-zero --format=pylint app > flake8.out
                '''
                recordIssues(
                    tools: [flake8(name: 'Flake8', pattern: 'flake8.out')],
                    qualityGates: [
                        [threshold: 8, type: 'TOTAL', unstable: true],
                        [threshold: 10, type: 'TOTAL', fail: true]
                    ]
                )
            }
        }

        stage('Security Test') {
            steps {
                bat '''
                    call venv\\Scripts\\activate
                    mkdir reports
                    bandit -r . -f custom -o reports\\bandit-report.txt
                    echo ========================
                    echo CONTENIDO DE REPORTS:
                    dir reports
                    echo ========================
                    type reports\\bandit-report.txt                    
                '''
            }
            post {
                always {
                    recordIssues tools: [pyLint(pattern: 'reports/bandit-report.txt')],
                        qualityGates: [
                            [threshold: 1, type: 'TOTAL', unstable: true],   // UNSTABLE si hay al menos 1
                            [threshold: 5, type: 'TOTAL', failure: true]     // FAILURE si hay más de 5
                        ]
                }
            }
        }

        
        stage('Performance') {
            steps {
                catchError(buildResult: 'SUCCESS', stageResult: 'SUCCESS') {
                    bat """
                        cd /d "${env.WORKSPACE}"
                        mkdir results
                        "C:\\apache-jmeter-5.6.3\\bin\\jmeter.bat" -n -t "C:\\Users\\USER\\Desktop\\Unir - Angela\\helloworld-master-Proyecto1\\test\\performance\\test-plan.jmx" -l results\\performance.jtl
                    """
                    archiveArtifacts artifacts: 'results\\performance.jtl', allowEmptyArchive: true
                }
            }
            post {
                always {
                    step([
                        $class: 'PerformancePublisher',
                        sourceDataFiles: 'results/performance.jtl',
                        modeOfThreshold: true,
                        configType: 'ART',
                        errorUnstableResponseTimeThreshold: '16',  // P90 calculado
                        errorFailedResponseTimeThreshold: '20',    // margen superior
                        nthBuildNumber: 0
                    ])
                }
            }
        }


    }
}