<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix = "c" uri = "http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>

    <%@ include file="../includes/head.jsp"%>

<body class="font-sans bg-gray-100">
<div id="app">

    <jsp:include page="../includes/nav.jsp"></jsp:include>          

    <div class=" min-h-screen">     
        <div class="py-4">
            <div class="max-w-4xl mx-auto px-4 sm:px-4 lg:px-4">

                <div class="bg-white rounded-lg shadow p-6">

                    <div>
                        <div class="flex justify-between">
                            <nav class="flex items-center text-sm leading-5 font-medium">
                                <a href="${contextUrl}/projects" class="text-gray-500 hover:text-gray-700 transition duration-150 ease-in-out">
                                    Home
                                </a>
                            </nav>
                            <c:if test="${project.createdBy == sessionScope.current_user.id && project.projectType != 'hq'}">
                                <div class="relative">
                                    <button v-on:click="isProjectActionsOpen = !isProjectActionsOpen" class="text-gray-800 font-bold z-10 relative">
                                        <svg class="fill-current w-4 h-4 mr-2" fill="none" stroke-linejoin="round" stroke-width="2" viewBox="0 0 20 20" stroke="currentColor">
                                            <path d="M5 12h.01M12 12h.01M19 12h.01M6 12a1 1 0 11-2 0 1 1 0 012 0zm7 0a1 1 0 11-2 0 1 1 0 012 0zm7 0a1 1 0 11-2 0 1 1 0 012 0z"></path>
                                        </svg>
                                    </button>
                                    <button v-if="isProjectActionsOpen" v-on:click="isProjectActionsOpen = false" tabindex="-1"
                                            class="fixed inset-0 w-full cursor-default border-0 outline-none"></button>

                                    <div v-cloak v-if="isProjectActionsOpen" class="absolute right-0 mt-2 py-2 w-24 bg-white rounded-lg shadow-xl">
                                        <a class="block px-4 py-1 text-gray-800 hover:bg-indigo-500 hover:text-white " href="${contextUrl}/project/${project.id}/edit">Edit</a>
                                        <a class="block px-4 py-1 text-red-600 hover:bg-red-500 hover:text-white"
                                           data-confirm="Are you sure?" rel="nofollow" data-method="delete"
                                           href="${contextUrl}/project/${project.id}/delete">Delete</a>
                                    </div>
                                </div>
                            </c:if>
                        </div>

                    </div>

                    <div class="mt-2">
                        <div class="flex justify-between">
                            <div class="text-xl font-bold text-gray-900 sm:text-md  sm:truncate">
                                ${project.name}
                            </div>
                        </div>
                        <div class="mt-1 flex items-center text-sm leading-5 text-gray-500">
                            ${project.description}
                        </div>

                        <div class="mt-4">
                            <div class="flex overflow-hidden">

                                <img class="-ml-1 first:ml-0 inline-block h-8 w-8 rounded-full text-white shadow-solid"
                                     src="${contextUrl}/avatar/${project.user.id}/${project.user.firstName.charAt(0)}${project.user.lastName.charAt(0)}.svg"
                                     title="${project.user.firstName} ${project.user.lastName}">

                                <c:forEach items="${project.users}" var="user">
                                    <img class="-ml-1 first:ml-0 inline-block h-8 w-8 rounded-full text-white shadow-solid" src="${contextUrl}/avatar/${user.id}/${user.firstName.charAt(0)}${user.lastName.charAt(0)}.svg"
                                         title="${user.firstName} ${user.lastName}">
                                </c:forEach>
                                <c:if test="${project.createdBy == sessionScope.current_user.id}">
                                    <a href="${contextUrl}/project/${project.id}/users" class="ml-2 text-xs text-gray-500 px-2 py-2 border border-gray-200 rounded-full">
                                        Add/Remove people
                                    </a>
                                </c:if>

                            </div>
                        </div>


                        <div class="mt-4">
                            <div class="flex flex-wrap -mx-3">         

                                <div class="w-full md:w-1/2 lg:w-1/3 px-3 mb-8">
                                    <a href="${contextUrl}/project/${project.id}/chat" class="no-underline bg-white rounded-lg shadow hover:shadow-raised hover:translateY-2px transition flex-1 flex flex-col border border-gray-200">
                                        <div class="p-6 flex flex-col">
                                            <h3 class="font-display text-black no-underline mb-2 font-bold">
                                                Chat
                                            </h3> <!-- TODO: Seed the HQ by default-->
                                            <div class="text-gray-700 text-sm">
                                                <%--
                                                Hash things out quickly, throw links and images back and forth, think out loud, share news, riff, and chat in real-time with your team
                                                --%>
                                                Chat casually with the group, ask random questions, and share stuff without ceremony.

                                            </div>
                                        </div>
                                    </a>
                                </div>

                                <div class="w-full md:w-1/2 lg:w-1/3 px-3 mb-8">
                                    <a href="${contextUrl}/project/${project.id}/files" class="no-underline bg-white rounded-lg shadow hover:shadow-raised hover:translateY-2px transition flex-1 flex flex-col border border-gray-200">
                                        <div class="p-6 flex flex-col">
                                            <h3 class="font-display text-black no-underline mb-2 font-bold">
                                                Docs & Files
                                            </h3> <!-- TODO: Seed the HQ by default-->
                                            <div class="text-gray-700 text-sm">
                                                This is a place to store and organize assets and reference material.
                                            </div>
                                        </div>
                                    </a>
                                </div>
                                
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>
<%@ include file="../includes/footer.jsp"%>

    <jsp:include page="../includes/notifications.jsp"></jsp:include>



