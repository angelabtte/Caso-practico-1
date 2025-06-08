pipeline {
    agent none

    stages {
        stage('Get Code') {
            agent { label 'ci-agent1' }
            steps {
                echo " Clonando repositorio..."
                bat 'whoami && hostname && echo %WORKSPACE%'
                git 'https://github.com/angelabtte/Caso-practico-1'
                stash name: 'source-code', includes: '**/*'
                bat 'dir /s /b'
            }
        }

        stage('Pruebas Paralelas') {
            parallel {
                stage('Unit Tests') {
                    agent { label 'ci-agent1' }
                    steps {
                        unstash 'source-code'
                        bat 'whoami && hostname && echo %WORKSPACE%'
                        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                            bat '''
                                set PYTHONPATH=%WORKSPACE%
                                dir test\unit
                                C:\Python\python.exe -m coverage run --branch --source=app -m pytest test\unit
                                C:\Python\python.exe -m coverage xml
                                C:\Python\python.exe -m coverage json -o coverage.json
                                C:\Python\python.exe -m coverage report --fail-under=0 --skip-covered > coverage.txt
                                type coverage.xml | more
                            '''
                        }
                        stash name: 'coverage-data', includes: 'coverage.xml'
                    }
                }

                stage('REST Tests') {
                    agent { label 'ci-agent2' }
                    steps {
                        unstash 'source-code'
                        bat 'whoami && hostname && echo %WORKSPACE%'
                        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                            bat '''
                                set FLASK_APP=app\api.py
                                start /B cmd /c "C:\Python\python.exe -m flask run --port=5000"
                                ping -n 10 127.0.0.1 >nul
                                cd /d "C:\Users\USER\Desktop\Unir - Angela\helloworld-master-Proyecto1\test"
                                start /B cmd /c "java -jar wiremock-standalone-3.13.0.jar --port 9090"
                                ping -n 10 127.0.0.1 >nul
                                cd /d "%WORKSPACE%"
                                C:\Python\python.exe -m pytest --junitxml=rest-report.xml test\rest
                            '''
                        }
                        junit 'rest-report.xml'
                    }
                }

                stage('Static Analysis') {
                    agent { label 'ci-agent3' }
                    steps {
                        unstash 'source-code'
                        bat 'whoami && hostname && echo %WORKSPACE%'
                        script {
                            def count = 0
                            catchError(buildResult: 'SUCCESS', stageResult: 'SUCCESS') {
                                def output = bat(script: 'C:\Python\python.exe -m flake8 . --count || exit /b 0', returnStdout: true).trim()
                                count = output.isInteger() ? output.toInteger() : 0
                            }

                            if (count >= 10) {
                                echo " Flake8 encontró ${count} hallazgos. Marcando etapa como FAILURE."
                                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                                    error("Flake8: demasiados hallazgos")
                                }
                            } else if (count >= 8) {
                                echo " Flake8 encontró ${count} hallazgos. Marcando etapa como UNSTABLE."
                                catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                                    error("Flake8: hallazgos moderados")
                                }
                            } else {
                                echo " Flake8 encontró ${count} hallazgos. Todo bien."
                            }
                        }
                    }
                }

                stage('Security Test') {
                    agent { label 'ci-agent3' }
                    steps {
                        unstash 'source-code'
                        bat 'whoami && hostname && echo %WORKSPACE%'
                        script {
                            def findings = 0
                            catchError(buildResult: 'SUCCESS', stageResult: 'SUCCESS') {
                                bat 'C:\Python\python.exe -m bandit -r . -f json -o bandit.json || exit /b 0'
                                def banditReport = readJSON file: 'bandit.json'
                                findings = banditReport.results.size()
                            }

                            if (findings >= 4) {
                                echo " Bandit encontró ${findings} hallazgos. Marcando etapa como FAILURE."
                                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                                    error("Bandit: demasiados hallazgos")
                                }
                            } else if (findings >= 2) {
                                echo " Bandit encontró ${findings} hallazgos. Marcando etapa como UNSTABLE."
                                catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                                    error("Bandit: hallazgos moderados")
                                }
                            } else {
                                echo " Bandit encontró ${findings} hallazgos. Todo bien."
                            }
                        }
                    }
                }

                stage('Performance') {
                    agent { label 'ci-agent2' }
                    steps {
                        unstash 'source-code'
                        bat 'whoami && hostname && echo %WORKSPACE%'
                        catchError(buildResult: 'SUCCESS', stageResult: 'SUCCESS') {
                            bat '''
                                cd /d "%WORKSPACE%"
                                "C:\apache-jmeter-5.6.3\bin\jmeter.bat" -n -t "C:\Users\USER\Desktop\Unir - Angela\helloworld-master-Proyecto1\test\performance\test-plan.jmx" -l results\performance.jtl
                            '''
                            archiveArtifacts artifacts: 'results\performance.jtl', allowEmptyArchive: true
                        }
                    }
                }
            }
        }

        stage('Coverage') {
            agent { label 'ci-agent1' }
            steps {
                unstash 'source-code'
                unstash 'coverage-data'
                bat 'whoami && hostname && echo %WORKSPACE%'
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

        stage('Cleanup') {
            agent any
            steps {
                cleanWs()
            }
        }
    }
}
