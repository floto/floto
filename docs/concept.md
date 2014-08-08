floto deployment concept
=========================

Requirements
------------

### Functional use cases

As a *software developer*  
I want to quickly integrate and test changes in a realistic system environment  
so that I can confidently implement bugfixes and new features.

As a *system engineer*  
I want to efficiently describe software installations  
so that I can build systems with reduced effort.

As a *system integrator*  
I want to easily configure the system to the customers need  
so that I can provide a tailored solution.

As a *system maintainer*  
I want to apply patches and updates to delivered systems  
so that I can quickly resolve issues and deliver new features.

As a *customer sysop*  
I want to redeploy machines  
so that I can keep the system operational.


### Non-functional requirements

#### Deterministic

Deploys should be deterministic and reproducible. This means especially that deploys should not depend on some prior or external state.

#### Fast

Deploys should be reasonably fast. Complete deployments should be possible in less than an hour. Minor updates should be pushed in less than a minute.

#### Programmable

Due to the variety of different usage scenarios, the configuration language should allow iteration, conditionals, function calls etc.

#### Nearly identical deployments in dev, QA and production environments

To avoid "But it works on _my_ machine" errors, deployment should work identically in dev and production enviroments

#### Offline capable

Since certain deployments occur at on-site locations with no internet connectivity, deployments should work in such a way that internet access can be avoided.

#### Server-less

To reduce infrastructure costs, especially on dev machines, deployments should not require a server component. A client based approach is preferred.

#### Windows compatible

Deployments should be possible from Windows hosts, to allow devs to work on Windows machines


Deployment phases
-----------------

### Build artifact

Compile java sources to create tar.gz/deb/jar.

*Requires*: Java sources, git repository, build environment (jenkins), access to mvn-repo (depending)

*Produces*: artifacts in maven repo (or local repo)

### Build VM image

Create a VM image from a template specification

*Requires*: template, VMWare workstation installation, packer, access to ISO, access to ubuntu repos (ubuntu and docker)

*Produces*: VM image

### Build docker image

Create a docker image from a deployment definition

*Requires*: deployment definition, access build VM, builder, internet access, docker repository?

*Produces*: docker image (in docker repository?)

### Deploy VM

Deploy a host VM in a production environment

*Requires*: ESX, VM image, deployment definition

*Produces*: Running host VM

### Deploy container

Deploy a running container in the target environment

*Requires*: Host VM, deployer, deployment definition, docker image, artifacts

*Produces*: Running container




Open issues
-----------

#### How are containers updated?
#### How can logfiles be accessed?
#### How are logfiles rotated?
#### How are containers/hosts monitored?

### closed
#### How is the bootstrap image built?
Packer





