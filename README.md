# floto [![Build Status](https://travis-ci.org/floto/floto.svg?branch=master)](https://travis-ci.org/floto/floto)

floto is a tool for deploying and orchestrating services using docker containers.

## Elevator pitch

floto allows you to describe your infrastructure using a domain-specific language (DSL) embedded in JavaScript.
Using such a description systems can be deployed and updated automatically.
The same descriptions can be used during development (on a local virtual machine) and in production (on bare metal or virtual machines).
Using docker containerization, these deployments are fast as well as isolated.
The description language and a templating mechanism allows services to publish their service endpoints, so that other services can access them.

## Prerequisites

* Docker 1.3
* Java 8

### Optional

* VMware Workstation
* VMware ESX
* VirtualBox